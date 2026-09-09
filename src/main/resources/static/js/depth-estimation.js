const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const uploadPreview = document.getElementById('upload-preview');
const colormapSelect = document.getElementById('colormap-select');
const estimateBtn = document.getElementById('estimate-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const originalImg = document.getElementById('original-img');
const depthImg = document.getElementById('depth-img');
const depthRange = document.getElementById('depth-range');

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

// Re-render with the new colormap without re-uploading
colormapSelect.addEventListener('change', () => {
  if (selectedFile && results.classList.contains('visible')) estimate();
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
  estimateBtn.disabled = false;
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
  estimateBtn.disabled = true;
  results.classList.remove('visible');
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

estimateBtn.addEventListener('click', estimate);

async function estimate() {
  if (!selectedFile) return;

  estimateBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');

  try {
    const form = new FormData();
    form.append('image', selectedFile);
    form.append('colormap', colormapSelect.value);

    const res = await fetch('/api/depth-estimation', {
      method: 'POST',
      body: form
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const min = res.headers.get('X-Depth-Min');
    const max = res.headers.get('X-Depth-Max');

    const blob = await res.blob();
    originalImg.src = URL.createObjectURL(selectedFile);
    depthImg.src = URL.createObjectURL(blob);

    if (min !== null && max !== null) {
      depthRange.textContent =
        `Depth range: ${Number(min).toFixed(3)} (furthest) to ${Number(max).toFixed(3)} (nearest)`;
    }

    results.classList.add('visible');
  } catch (err) {
    showError(err.message);
  } finally {
    estimateBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}
