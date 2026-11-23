const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const uploadPreview = document.getElementById('upload-preview');
const modelSelect = document.getElementById('model-select');
const thresholdInput = document.getElementById('threshold-input');
const thresholdValue = document.getElementById('threshold-value');
const detectBtn = document.getElementById('detect-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const originalImg = document.getElementById('original-img');
const annotatedImg = document.getElementById('annotated-img');
const timing = document.getElementById('timing');
const people = document.getElementById('people');

const MAX_SIZE = 4 * 1024 * 1024;
let selectedFile = null;

fetch('/api/pose-detection/models')
  .then(res => res.json())
  .then(models => {
    modelSelect.innerHTML = models
      .map(m => `<option value="${m.id}">${m.label} — ${m.size}</option>`)
      .join('');
  })
  .catch(() => { /* keep the default option */ });

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

thresholdInput.addEventListener('input', () => {
  thresholdValue.textContent = Number(thresholdInput.value).toFixed(2);
});

// Re-run with the new model or threshold without re-uploading
modelSelect.addEventListener('change', rerun);
thresholdInput.addEventListener('change', rerun);

function rerun() {
  if (selectedFile && results.classList.contains('visible')) detect();
}

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
  detectBtn.disabled = false;
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
  detectBtn.disabled = true;
  results.classList.remove('visible');
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

detectBtn.addEventListener('click', detect);

async function detect() {
  if (!selectedFile) return;

  detectBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');

  try {
    const form = new FormData();
    form.append('image', selectedFile);
    form.append('model', modelSelect.value);
    form.append('threshold', thresholdInput.value);

    const res = await fetch('/api/pose-detection', { method: 'POST', body: form });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();

    originalImg.src = URL.createObjectURL(selectedFile);
    annotatedImg.src = 'data:image/png;base64,' + data.image;
    timing.textContent = `${data.model} · ${data.millis} ms`;
    renderPeople(data.people);
    results.classList.add('visible');
  } catch (err) {
    showError(err.message);
  } finally {
    detectBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderPeople(list) {
  if (!list.length) {
    people.innerHTML = '<p class="text-muted">No people found. Try lowering the person confidence.</p>';
    return;
  }
  people.innerHTML = list.map(p => `
    <div class="pose-person">
      <div class="detection-row">
        <span class="detection-swatch" style="background:${p.color}"></span>
        <span class="detection-label">Person ${p.number}</span>
        <span class="text-muted">${(p.confidence * 100).toFixed(0)}% · ${p.visibleKeypoints}/17 keypoints visible</span>
      </div>
      <div class="pose-angles">
        ${p.angles.map(a => `
          <div class="pose-angle${a.degrees === null ? ' pose-angle--hidden' : ''}">
            <span class="pose-angle-name">${a.name}</span>
            <span class="pose-angle-value">${a.degrees === null ? '—' : a.degrees + '°'}</span>
          </div>`).join('')}
      </div>
    </div>`).join('');
}
