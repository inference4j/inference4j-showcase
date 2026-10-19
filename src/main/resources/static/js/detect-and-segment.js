const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const uploadPreview = document.getElementById('upload-preview');
const runBtn = document.getElementById('run-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const originalImg = document.getElementById('original-img');
const segmentedImg = document.getElementById('segmented-img');
const timing = document.getElementById('timing');
const objectsBox = document.getElementById('objects');

const MAX_SIZE = 4 * 1024 * 1024;
let selectedFile = null;

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
  runBtn.disabled = false;
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
  runBtn.disabled = true;
  results.classList.remove('visible');
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

runBtn.addEventListener('click', run);

async function run() {
  if (!selectedFile) return;
  runBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');

  try {
    const form = new FormData();
    form.append('image', selectedFile);
    const res = await fetch('/api/segmentation/detect', { method: 'POST', body: form });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();

    originalImg.src = URL.createObjectURL(selectedFile);
    segmentedImg.src = 'data:image/png;base64,' + data.image;
    timing.textContent =
      `detect ${data.detectMillis} ms · encode ${data.encodeMillis} ms · masks ${data.segmentMillis} ms`;
    renderObjects(data.objects);
    results.classList.add('visible');
  } catch (err) {
    showError(err.message);
  } finally {
    runBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderObjects(objects) {
  objectsBox.innerHTML = '';
  if (objects.length === 0) {
    objectsBox.innerHTML = '<p class="text-muted">No objects detected.</p>';
    return;
  }
  objects.forEach((o, i) => {
    const item = document.createElement('figure');
    item.className = 'seg-object';
    item.style.setProperty('--swatch', o.color);

    const img = document.createElement('img');
    img.alt = o.label;
    if (o.cutout) img.src = 'data:image/png;base64,' + o.cutout;

    const caption = document.createElement('figcaption');
    const name = document.createElement('strong');
    name.textContent = o.label;
    const detail = document.createElement('span');
    detail.className = 'text-muted';
    detail.textContent = `box ${(o.confidence * 100).toFixed(0)}% · mask ${(o.maskScore * 100).toFixed(0)}%`;
    caption.append(name, detail);

    item.append(img, caption);
    if (o.cutout) {
      const link = document.createElement('a');
      link.href = img.src;
      link.download = `${o.label}-${i + 1}.png`;
      link.textContent = 'Download';
      link.className = 'link-btn';
      caption.appendChild(link);
    }
    objectsBox.appendChild(item);
  });
}
