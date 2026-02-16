const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('audio-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const audioPreview = document.getElementById('audio-preview');
const fileNameEl = document.getElementById('file-name');
const player = document.getElementById('player');
const detectBtn = document.getElementById('detect-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const timeline = document.getElementById('timeline');
const playhead = document.getElementById('playhead');
const timeStart = document.getElementById('time-start');
const timeEnd = document.getElementById('time-end');
const segmentsList = document.getElementById('segments-list');

const MAX_SIZE = 4 * 1024 * 1024;
let selectedFile = null;
let totalDuration = 0;
let animationId = null;

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
  fileNameEl.textContent = file.name;
  player.src = URL.createObjectURL(file);
  audioPreview.hidden = false;
  clearBtn.hidden = false;
  dropZone.querySelector('.drop-zone-text').hidden = true;
  detectBtn.disabled = false;
  error.classList.remove('visible');
  results.classList.remove('visible');
  stopPlayhead();
}

function clearSelection() {
  selectedFile = null;
  fileInput.value = '';
  player.src = '';
  audioPreview.hidden = true;
  clearBtn.hidden = true;
  dropZone.querySelector('.drop-zone-text').hidden = false;
  detectBtn.disabled = true;
  results.classList.remove('visible');
  stopPlayhead();
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

function formatTime(s) {
  const min = Math.floor(s / 60);
  const sec = Math.floor(s % 60);
  return `${min}:${sec.toString().padStart(2, '0')}`;
}

detectBtn.addEventListener('click', detect);

async function detect() {
  if (!selectedFile) return;

  detectBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');
  stopPlayhead();

  try {
    const form = new FormData();
    form.append('audio', selectedFile);

    const res = await fetch('/api/voice-activity', {
      method: 'POST',
      body: form
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const data = await res.json();
    renderTimeline(data);
    startPlayhead();
  } catch (err) {
    showError(err.message);
  } finally {
    detectBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderTimeline(data) {
  totalDuration = data.totalDuration;

  // Clear previous segments
  timeline.querySelectorAll('.timeline-segment').forEach(el => el.remove());

  // Draw speech segments
  data.segments.forEach(seg => {
    const el = document.createElement('div');
    el.className = 'timeline-segment';
    const left = (seg.start / totalDuration) * 100;
    const width = ((seg.end - seg.start) / totalDuration) * 100;
    el.style.left = `${left}%`;
    el.style.width = `${width}%`;
    timeline.appendChild(el);
  });

  timeStart.textContent = formatTime(0);
  timeEnd.textContent = formatTime(totalDuration);

  // Segments list
  segmentsList.innerHTML = '';
  data.segments.forEach((seg, i) => {
    const row = document.createElement('div');
    row.className = 'segment-row';
    row.innerHTML = `
      <span class="segment-index">#${i + 1}</span>
      <span class="segment-time">${formatTime(seg.start)} — ${formatTime(seg.end)}</span>
      <span class="segment-duration">${(seg.end - seg.start).toFixed(2)}s</span>
      <span class="segment-confidence">${(seg.confidence * 100).toFixed(0)}%</span>
    `;
    segmentsList.appendChild(row);
  });

  playhead.style.left = '0%';
  results.classList.add('visible');
}

function startPlayhead() {
  stopPlayhead();

  player.addEventListener('play', onPlay);
  player.addEventListener('pause', onPause);
  player.addEventListener('ended', onPause);
  player.addEventListener('seeked', onSeek);
}

function stopPlayhead() {
  if (animationId) cancelAnimationFrame(animationId);
  animationId = null;
  player.removeEventListener('play', onPlay);
  player.removeEventListener('pause', onPause);
  player.removeEventListener('ended', onPause);
  player.removeEventListener('seeked', onSeek);
}

function onPlay() {
  tick();
}

function onPause() {
  if (animationId) cancelAnimationFrame(animationId);
  animationId = null;
}

function onSeek() {
  updatePlayheadPosition();
}

function tick() {
  updatePlayheadPosition();
  animationId = requestAnimationFrame(tick);
}

function updatePlayheadPosition() {
  if (totalDuration <= 0) return;
  const pct = (player.currentTime / totalDuration) * 100;
  playhead.style.left = `${Math.min(pct, 100)}%`;
}
