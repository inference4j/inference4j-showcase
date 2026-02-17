const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const preview = document.getElementById('preview');
const labelsInput = document.getElementById('labels-input');
const classifyBtn = document.getElementById('classify-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const resultsBody = document.getElementById('results-body');

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

labelsInput.addEventListener('input', updateClassifyState);

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    labelsInput.value = btn.dataset.labels;
    updateClassifyState();
    labelsInput.focus();
  });
});

function selectFile(file) {
  if (file.size > MAX_SIZE) {
    showError('Image exceeds 4 MB limit');
    return;
  }
  selectedFile = file;
  const url = URL.createObjectURL(file);
  preview.src = url;
  preview.hidden = false;
  clearBtn.hidden = false;
  dropZone.querySelector('.drop-zone-text').hidden = true;
  error.classList.remove('visible');
  results.classList.remove('visible');
  updateClassifyState();
}

function clearSelection() {
  selectedFile = null;
  fileInput.value = '';
  preview.hidden = true;
  preview.src = '';
  clearBtn.hidden = true;
  dropZone.querySelector('.drop-zone-text').hidden = false;
  results.classList.remove('visible');
  updateClassifyState();
}

function updateClassifyState() {
  const hasLabels = labelsInput.value.split(',').some(s => s.trim().length > 0);
  classifyBtn.disabled = !selectedFile || !hasLabels;
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

classifyBtn.addEventListener('click', classify);

async function classify() {
  if (!selectedFile) return;

  classifyBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const form = new FormData();
    form.append('image', selectedFile);
    form.append('labels', labelsInput.value);

    const res = await fetch('/api/visual-search', {
      method: 'POST',
      body: form
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const data = await res.json();
    renderResults(data);
  } catch (err) {
    showError(err.message);
  } finally {
    updateClassifyState();
    spinner.classList.remove('visible');
  }
}

function renderResults(classifications) {
  resultsBody.innerHTML = '';

  classifications
    .sort((a, b) => b.confidence - a.confidence)
    .forEach(({ label, confidence }) => {
      const pct = (confidence * 100).toFixed(1);

      const item = document.createElement('div');
      item.className = 'result-item';
      item.innerHTML = `
        <span class="result-label">${label}</span>
        <div class="result-bar-track">
          <div class="result-bar-fill positive" style="width: 0%"></div>
        </div>
        <span class="result-score">${pct}%</span>
      `;
      resultsBody.appendChild(item);

      requestAnimationFrame(() => {
        item.querySelector('.result-bar-fill').style.width = `${pct}%`;
      });
    });

  results.classList.add('visible');
}
