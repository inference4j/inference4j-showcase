const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const uploadPreview = document.getElementById('upload-preview');
const readBtn = document.getElementById('read-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const annotatedImg = document.getElementById('annotated-img');
const linesList = document.getElementById('lines');
const summary = document.getElementById('summary');
const copyBtn = document.getElementById('copy-btn');
const modelsSelect = document.getElementById('models-select');
const detectorSelect = document.getElementById('detector-select');
const deviceSelect = document.getElementById('device-select');
const timing = document.getElementById('timing');

loadAccelerators(deviceSelect);

const MAX_SIZE = 4 * 1024 * 1024;
let selectedFile = null;
let lastText = '';

browseBtn.addEventListener('click', () => fileInput.click());

fileInput.addEventListener('change', () => {
  if (fileInput.files.length) selectFile(fileInput.files[0]);
});

dropZone.addEventListener('dragover', e => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});

dropZone.addEventListener('dragleave', () => {
  dropZone.classList.remove('drag-over');
});

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

function selectFile(file) {
  if (file.size > MAX_SIZE) {
    showError('Image exceeds 4 MB limit');
    return;
  }
  selectedFile = file;
  uploadPreview.src = URL.createObjectURL(file);
  uploadPreview.hidden = false;
  clearBtn.hidden = false;
  dropZone.querySelector('.drop-zone-text').hidden = true;
  readBtn.disabled = false;
  error.classList.remove('visible');
  results.classList.remove('visible');
}

function clearSelection() {
  selectedFile = null;
  fileInput.value = '';
  uploadPreview.hidden = true;
  uploadPreview.src = '';
  clearBtn.hidden = true;
  dropZone.querySelector('.drop-zone-text').hidden = false;
  readBtn.disabled = true;
  results.classList.remove('visible');
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

readBtn.addEventListener('click', read);

copyBtn.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(lastText);
    copyBtn.textContent = 'Copied';
  } catch {
    copyBtn.textContent = 'Copy failed';
  }
  setTimeout(() => { copyBtn.textContent = 'Copy text'; }, 1500);
});

async function read() {
  if (!selectedFile) return;

  readBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const form = new FormData();
    form.append('image', selectedFile);
    form.append('models', modelsSelect.value);
    form.append('detector', detectorSelect.value);
    form.append('device', deviceSelect.value);

    const res = await fetch('/api/ocr', { method: 'POST', body: form });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();

    annotatedImg.src = data.annotatedImage;
    renderLines(data.lines);
    const count = `${data.lines.length} line${data.lines.length === 1 ? '' : 's'}`;
    summary.textContent = `— ${count}`;
    timing.textContent = timingText(data.device, data.millis, count);
    lastText = data.lines.map(l => l.text).join('\n');
    copyBtn.disabled = data.lines.length === 0;
    results.classList.add('visible');
  } catch (err) {
    showError(err.message);
  } finally {
    readBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderLines(lines) {
  linesList.innerHTML = '';
  if (lines.length === 0) {
    const empty = document.createElement('li');
    empty.className = 'ocr-empty';
    empty.textContent = 'No text found.';
    linesList.appendChild(empty);
    return;
  }
  for (const line of lines) {
    const item = document.createElement('li');
    item.className = 'ocr-line';

    const index = document.createElement('span');
    index.className = 'ocr-index';
    index.textContent = line.index;

    const body = document.createElement('div');
    body.className = 'ocr-body';

    const text = document.createElement('span');
    text.className = 'ocr-text';
    text.textContent = line.text;

    const meta = document.createElement('div');
    meta.className = 'ocr-meta';
    const track = document.createElement('div');
    track.className = 'result-bar-track';
    const fill = document.createElement('div');
    fill.className = 'result-bar-fill positive';
    fill.style.width = `${(line.confidence * 100).toFixed(1)}%`;
    track.appendChild(fill);
    const score = document.createElement('span');
    score.className = 'result-score';
    score.textContent = `${(line.confidence * 100).toFixed(1)}%`;
    meta.append(track, score);

    body.append(text, meta);
    item.append(index, body);
    linesList.appendChild(item);
  }
}
