const textarea = document.getElementById('input-text');
const modelSelect = document.getElementById('model-select');
const labelsField = document.getElementById('labels-field');
const labelsInput = document.getElementById('labels-input');
const modelHint = document.getElementById('model-hint');
const recognizeBtn = document.getElementById('recognize-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const annotatedText = document.getElementById('annotated-text');
const entitiesBody = document.getElementById('entities-body');
const summary = document.getElementById('summary');

const LABEL_NAMES = {
  PER: 'Person',
  ORG: 'Organization',
  LOC: 'Location',
  MISC: 'Miscellaneous'
};

function updateModel() {
  const gliner = modelSelect.value === 'gliner';
  labelsField.hidden = !gliner;
  modelHint.textContent = gliner
    ? 'Zero-shot: name any entity types in plain words, such as "invoice id" or "medication".'
    : 'Trained on news text: the most accurate choice for people, organizations and locations.';
}
modelSelect.addEventListener('change', updateModel);
updateModel();

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    if (btn.dataset.model) {
      modelSelect.value = btn.dataset.model;
      labelsInput.value = btn.dataset.labels;
      updateModel();
    }
    textarea.focus();
  });
});

recognizeBtn.addEventListener('click', recognize);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') recognize();
});

async function recognize() {
  const text = textarea.value.trim();
  if (!text) return;
  const model = modelSelect.value;
  const labels = labelsInput.value.split(',').map(l => l.trim()).filter(Boolean);
  if (model === 'gliner' && labels.length === 0) {
    error.textContent = 'Enter at least one entity type';
    error.classList.add('visible');
    return;
  }

  recognizeBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/ner', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, model, labels })
    });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();
    summary.textContent = `— ${data.model} · ${data.millis} ms`;
    renderAnnotated(annotatedText, text, data.entities);
    renderEntityList(entitiesBody, data.entities, l => LABEL_NAMES[l] || l);
    results.classList.add('visible');
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    recognizeBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}
