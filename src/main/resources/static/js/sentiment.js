const textarea = document.getElementById('input-text');
const analyzeBtn = document.getElementById('analyze-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const resultsBody = document.getElementById('results-body');

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    textarea.focus();
  });
});

analyzeBtn.addEventListener('click', analyze);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') analyze();
});

async function analyze() {
  const text = textarea.value.trim();
  if (!text) return;

  analyzeBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/sentiment', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: text
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const data = await res.json();
    renderResults(data);
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    analyzeBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderResults(classifications) {
  resultsBody.innerHTML = '';

  classifications
    .sort((a, b) => b.confidence - a.confidence)
    .forEach(({ label, confidence }) => {
      const pct = (confidence * 100).toFixed(1);
      const cls = label.toLowerCase().includes('positive') ? 'positive' : 'negative';

      const item = document.createElement('div');
      item.className = 'result-item';
      item.innerHTML = `
        <span class="result-label">${label}</span>
        <div class="result-bar-track">
          <div class="result-bar-fill ${cls}" style="width: 0%"></div>
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
