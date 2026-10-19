const textarea = document.getElementById('input-text');
const checkBtn = document.getElementById('check-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const verdict = document.getElementById('verdict');
const bar = document.getElementById('bar');
const score = document.getElementById('score');

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    textarea.focus();
  });
});

checkBtn.addEventListener('click', check);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') check();
});

async function check() {
  const text = textarea.value.trim();
  if (!text) return;

  checkBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/prompt-injection', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: text
    });
    if (!res.ok) throw new Error(`Server error (${res.status})`);
    const data = await res.json();
    const pct = (data.probability * 100).toFixed(1);
    verdict.className = `verdict ${data.injection ? 'injection' : 'safe'}`;
    verdict.innerHTML = '';
    const title = document.createElement('span');
    title.textContent = data.injection ? 'Prompt injection detected' : 'Looks safe';
    const detail = document.createElement('span');
    detail.className = 'verdict-detail';
    detail.textContent = `threshold ${(data.threshold * 100).toFixed(0)}% · ${data.millis} ms`;
    verdict.append(title, detail);
    bar.className = `result-bar-fill ${data.injection ? 'negative' : 'positive'}`;
    bar.style.width = '0%';
    score.textContent = `${pct}%`;
    results.classList.add('visible');
    requestAnimationFrame(() => { bar.style.width = `${pct}%`; });
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    checkBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}
