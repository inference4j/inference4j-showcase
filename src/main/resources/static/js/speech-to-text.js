const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('audio-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const audioPreview = document.getElementById('audio-preview');
const fileName = document.getElementById('file-name');
const player = document.getElementById('player');
const transcribeBtn = document.getElementById('transcribe-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const transcription = document.getElementById('transcription');

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
  if (file) selectFile(file);
});

clearBtn.addEventListener('click', e => {
  e.stopPropagation();
  clearSelection();
});

function selectFile(file) {
  if (file.size > MAX_SIZE) {
    showError('File exceeds 4 MB limit');
    return;
  }
  selectedFile = file;
  fileName.textContent = file.name;
  player.src = URL.createObjectURL(file);
  audioPreview.hidden = false;
  clearBtn.hidden = false;
  dropZone.querySelector('.drop-zone-text').hidden = true;
  transcribeBtn.disabled = false;
  error.classList.remove('visible');
  results.classList.remove('visible');
}

function clearSelection() {
  selectedFile = null;
  fileInput.value = '';
  player.src = '';
  audioPreview.hidden = true;
  clearBtn.hidden = true;
  dropZone.querySelector('.drop-zone-text').hidden = false;
  transcribeBtn.disabled = true;
  results.classList.remove('visible');
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

transcribeBtn.addEventListener('click', transcribe);

async function transcribe() {
  if (!selectedFile) return;

  transcribeBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const form = new FormData();
    form.append('audio', selectedFile);

    const res = await fetch('/api/speech-to-text', {
      method: 'POST',
      body: form
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const data = await res.json();
    transcription.textContent = data.text;
    results.classList.add('visible');
  } catch (err) {
    showError(err.message);
  } finally {
    transcribeBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}
