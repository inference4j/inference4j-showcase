const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const uploadPreview = document.getElementById('upload-preview');
const spinner = document.getElementById('spinner');
const status = document.getElementById('status');
const error = document.getElementById('error');
const results = document.getElementById('results');
const canvas = document.getElementById('stage');
const ctx = canvas.getContext('2d');
const timing = document.getElementById('timing');
const undoBtn = document.getElementById('undo-btn');
const resetBtn = document.getElementById('reset-btn');
const downloadBtn = document.getElementById('download-btn');
const candidatesBox = document.getElementById('candidates');
const candidateList = document.getElementById('candidate-list');
const toolButtons = document.querySelectorAll('.seg-tool');

const MAX_SIZE = 4 * 1024 * 1024;

// The image as the server encoded it (it may be scaled down), and the prompt built on it
let session = null;      // { id, width, height }
let photo = null;        // HTMLImageElement at the encoded size
let tool = 'include';
let clicks = [];         // [{ x, y, foreground }]
let box = null;          // { x1, y1, x2, y2 }
let history = [];        // undo stack of { clicks, box }
let candidates = [];     // [{ image, score, area }]
let selected = 0;
let dragStart = null;
let dragBox = null;
let requestSeq = 0;

// --- Upload ---

browseBtn.addEventListener('click', () => fileInput.click());
fileInput.addEventListener('change', () => {
  if (fileInput.files.length) selectFile(fileInput.files[0]);
});
dropZone.addEventListener('dragover', e => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});
dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
dropZone.addEventListener('drop', e => {
  e.preventDefault();
  dropZone.classList.remove('drag-over');
  const file = e.dataTransfer.files[0];
  if (file && file.type.startsWith('image/')) selectFile(file);
});
clearBtn.addEventListener('click', e => {
  e.stopPropagation();
  clearSelection();
});

async function selectFile(file) {
  if (file.size > MAX_SIZE) {
    showError('Image exceeds 4 MB limit');
    return;
  }
  uploadPreview.src = URL.createObjectURL(file);
  uploadPreview.hidden = false;
  clearBtn.hidden = false;
  dropZone.querySelector('.drop-zone-text').hidden = true;
  error.classList.remove('visible');
  results.classList.remove('visible');
  await encode(file);
}

function clearSelection() {
  fileInput.value = '';
  uploadPreview.hidden = true;
  uploadPreview.src = '';
  clearBtn.hidden = true;
  dropZone.querySelector('.drop-zone-text').hidden = false;
  results.classList.remove('visible');
  status.textContent = '';
  session = null;
}

async function encode(file) {
  spinner.classList.add('visible');
  status.textContent = 'Encoding the image…';
  try {
    const form = new FormData();
    form.append('image', file);
    const res = await fetch('/api/segmentation/encode', { method: 'POST', body: form });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    session = await res.json();

    photo = await loadImage(URL.createObjectURL(file));
    canvas.width = session.width;
    canvas.height = session.height;
    resetPrompt();
    status.textContent = `Encoded in ${session.millis} ms. Now click an object.`;
    results.classList.add('visible');
  } catch (err) {
    status.textContent = '';
    showError(err.message);
  } finally {
    spinner.classList.remove('visible');
  }
}

// --- Tools ---

toolButtons.forEach(btn => btn.addEventListener('click', () => {
  tool = btn.dataset.tool;
  toolButtons.forEach(b => b.setAttribute('aria-pressed', String(b === btn)));
}));

undoBtn.addEventListener('click', () => {
  const previous = history.pop();
  if (!previous) return;
  clicks = previous.clicks;
  box = previous.box;
  promptChanged();
});

resetBtn.addEventListener('click', resetPrompt);

downloadBtn.addEventListener('click', () => {
  const mask = candidates[selected]?.image;
  if (!mask) return;
  // Keep the photo only where the mask is opaque
  const out = document.createElement('canvas');
  out.width = session.width;
  out.height = session.height;
  const o = out.getContext('2d');
  o.drawImage(photo, 0, 0, out.width, out.height);
  o.globalCompositeOperation = 'destination-in';
  o.drawImage(mask, 0, 0);
  out.toBlob(blob => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'cutout.png';
    a.click();
  }, 'image/png');
});

function resetPrompt() {
  clicks = [];
  box = null;
  history = [];
  candidates = [];
  selected = 0;
  timing.textContent = '';
  updateButtons();
  renderCandidates();
  draw();
}

// --- Canvas input ---

function toImageCoords(e) {
  const rect = canvas.getBoundingClientRect();
  return {
    x: (e.clientX - rect.left) * canvas.width / rect.width,
    y: (e.clientY - rect.top) * canvas.height / rect.height,
  };
}

canvas.addEventListener('pointerdown', e => {
  if (!session) return;
  const p = toImageCoords(e);
  if (tool === 'box') {
    dragStart = p;
    dragBox = { x1: p.x, y1: p.y, x2: p.x, y2: p.y };
    canvas.setPointerCapture(e.pointerId);
    return;
  }
  if (tool === 'exclude' && clicks.length === 0 && !box) {
    showError('Include part of the object first, then exclude what does not belong');
    return;
  }
  remember();
  clicks = [...clicks, { x: p.x, y: p.y, foreground: tool === 'include' }];
  promptChanged();
});

canvas.addEventListener('pointermove', e => {
  if (!dragStart) return;
  const p = toImageCoords(e);
  dragBox = { x1: dragStart.x, y1: dragStart.y, x2: p.x, y2: p.y };
  draw();
});

canvas.addEventListener('pointerup', () => {
  if (!dragStart) return;
  const b = dragBox;
  dragStart = null;
  dragBox = null;
  // Ignore a click without a drag in box mode
  if (Math.abs(b.x2 - b.x1) < 4 || Math.abs(b.y2 - b.y1) < 4) {
    draw();
    return;
  }
  remember();
  box = {
    x1: Math.min(b.x1, b.x2), y1: Math.min(b.y1, b.y2),
    x2: Math.max(b.x1, b.x2), y2: Math.max(b.y1, b.y2),
  };
  promptChanged();
});

function remember() {
  history.push({ clicks, box });
}

// --- Segment ---

async function promptChanged() {
  updateButtons();
  draw();
  if (clicks.length === 0 && !box) {
    candidates = [];
    renderCandidates();
    draw();
    return;
  }

  const seq = ++requestSeq;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  try {
    const res = await fetch('/api/segmentation/segment', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: session.id, clicks, box }),
    });
    if (res.status === 410) throw new Error('This image expired on the server, please upload it again');
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();
    // A newer click may have answered first
    if (seq !== requestSeq) return;

    candidates = await Promise.all(data.candidates.map(async c => ({
      image: await loadImage('data:image/png;base64,' + c.mask),
      score: c.score,
      area: c.area,
    })));
    selected = 0;
    timing.textContent = `${data.millis} ms`;
    renderCandidates();
    draw();
  } catch (err) {
    showError(err.message);
  } finally {
    if (seq === requestSeq) spinner.classList.remove('visible');
    updateButtons();
  }
}

function renderCandidates() {
  candidateList.innerHTML = '';
  candidatesBox.hidden = candidates.length === 0;
  candidates.forEach((c, i) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'seg-candidate';
    btn.setAttribute('aria-pressed', String(i === selected));
    const thumb = document.createElement('canvas');
    thumb.width = 160;
    thumb.height = Math.round(160 * session.height / session.width);
    const t = thumb.getContext('2d');
    t.drawImage(photo, 0, 0, thumb.width, thumb.height);
    t.globalAlpha = 0.6;
    t.drawImage(c.image, 0, 0, thumb.width, thumb.height);
    const caption = document.createElement('span');
    caption.textContent = `${(c.score * 100).toFixed(0)}% · ${c.area.toLocaleString()} px`;
    btn.append(thumb, caption);
    btn.addEventListener('click', () => {
      selected = i;
      renderCandidates();
      draw();
    });
    candidateList.appendChild(btn);
  });
}

// --- Drawing ---

function draw() {
  if (!photo) return;
  ctx.globalAlpha = 1;
  ctx.drawImage(photo, 0, 0, canvas.width, canvas.height);

  const mask = candidates[selected]?.image;
  if (mask) {
    ctx.globalAlpha = 0.5;
    ctx.drawImage(mask, 0, 0);
    ctx.globalAlpha = 1;
  }

  const unit = Math.max(canvas.width, canvas.height) / 200;
  ctx.lineWidth = Math.max(2, unit / 2);
  for (const b of [box, dragBox].filter(Boolean)) {
    ctx.strokeStyle = '#f59e0b';
    ctx.setLineDash([unit * 2, unit]);
    ctx.strokeRect(b.x1, b.y1, b.x2 - b.x1, b.y2 - b.y1);
    ctx.setLineDash([]);
  }
  for (const c of clicks) {
    ctx.beginPath();
    ctx.arc(c.x, c.y, Math.max(5, unit * 1.6), 0, Math.PI * 2);
    ctx.fillStyle = c.foreground ? '#10b981' : '#ef4444';
    ctx.fill();
    ctx.strokeStyle = '#fff';
    ctx.stroke();
  }
}

function updateButtons() {
  undoBtn.disabled = history.length === 0;
  resetBtn.disabled = clicks.length === 0 && !box;
  downloadBtn.disabled = candidates.length === 0;
}

// --- Helpers ---

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Could not load image'));
    img.src = src;
  });
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}
