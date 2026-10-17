const textarea = document.getElementById('input-text');
const labelsInput = document.getElementById('labels-input');
const modelSelect = document.getElementById('model-select');
const modeSelect = document.getElementById('mode-select');
const modeHint = document.getElementById('mode-hint');
const classifyBtn = document.getElementById('classify-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const resultsBody = document.getElementById('results-body');
const summary = document.getElementById('summary');

function updateModeHint() {
  modeHint.textContent = modeSelect.value === 'multi'
    ? 'Each label is judged on its own (0–100%): several labels, or none, can apply.'
    : 'Labels compete: the scores add up to 100%.';
}
modeSelect.addEventListener('change', updateModeHint);
updateModeHint();

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    labelsInput.value = btn.dataset.labels;
    modeSelect.value = btn.dataset.mode || 'single';
    updateModeHint();
    textarea.focus();
  });
});

classifyBtn.addEventListener('click', classify);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') classify();
});

async function classify() {
  const text = textarea.value.trim();
  const labels = labelsInput.value.split(',').map(l => l.trim()).filter(Boolean);
  if (!text || labels.length === 0) {
    error.textContent = 'Enter a text and at least one label';
    error.classList.add('visible');
    return;
  }

  classifyBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/zero-shot', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, labels, model: modelSelect.value, multiLabel: modeSelect.value === 'multi' })
    });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();
    summary.textContent = `— ${data.model} · ${data.millis} ms · template "${data.template}"`;
    renderResults(data.results);
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    classifyBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderResults(classifications) {
  resultsBody.innerHTML = '';
  classifications.forEach(({ label, confidence }, i) => {
    const pct = (confidence * 100).toFixed(1);
    const item = document.createElement('div');
    item.className = 'result-item';

    const name = document.createElement('span');
    name.className = 'result-label';
    name.textContent = label;

    const track = document.createElement('div');
    track.className = 'result-bar-track';
    const fill = document.createElement('div');
    fill.className = `result-bar-fill ${i === 0 ? 'positive' : 'neutral'}`;
    fill.style.width = '0%';
    track.appendChild(fill);

    const score = document.createElement('span');
    score.className = 'result-score';
    score.textContent = `${pct}%`;

    item.append(name, track, score);
    resultsBody.appendChild(item);
    requestAnimationFrame(() => { fill.style.width = `${pct}%`; });
  });
  results.classList.add('visible');
}
