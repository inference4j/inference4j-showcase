const PII_TYPES = ['person', 'email', 'phone number', 'address', 'date of birth', 'credit card number',
  'bank account number', 'social security number', 'passport number', 'ip address'];

const textarea = document.getElementById('input-text');
const typesBox = document.getElementById('pii-types');
const customInput = document.getElementById('custom-input');
const detectBtn = document.getElementById('detect-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const annotatedText = document.getElementById('annotated-text');
const redactedText = document.getElementById('redacted-text');
const entitiesBody = document.getElementById('entities-body');
const summary = document.getElementById('summary');
const copyBtn = document.getElementById('copy-btn');

PII_TYPES.forEach(type => {
  const label = document.createElement('label');
  label.className = 'pii-type';
  label.innerHTML = `<input type="checkbox" value="${type}" checked> ${type}`;
  typesBox.appendChild(label);
});

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    customInput.value = btn.dataset.custom || '';
    textarea.focus();
  });
});

copyBtn.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(redactedText.textContent);
    copyBtn.textContent = 'Copied';
    setTimeout(() => { copyBtn.textContent = 'Copy'; }, 1500);
  } catch {
    copyBtn.textContent = 'Copy failed';
  }
});

detectBtn.addEventListener('click', detect);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') detect();
});

async function detect() {
  const text = textarea.value.trim();
  const labels = [...typesBox.querySelectorAll('input:checked')].map(c => c.value)
    .concat(customInput.value.split(',').map(l => l.trim()).filter(Boolean));
  if (!text || labels.length === 0) {
    error.textContent = 'Enter a text and pick at least one type';
    error.classList.add('visible');
    return;
  }

  detectBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/pii', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, labels })
    });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();
    const n = data.entities.length;
    summary.textContent = `— ${n} ${n === 1 ? 'entity' : 'entities'} · GLiNER PII · ${data.millis} ms`;
    renderAnnotated(annotatedText, text, data.entities);
    redactedText.textContent = data.redacted;
    renderEntityList(entitiesBody, data.entities);
    results.classList.add('visible');
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    detectBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}
