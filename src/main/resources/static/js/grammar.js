const textarea = document.getElementById('input-text');
const correctBtn = document.getElementById('correct-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const originalText = document.getElementById('original-text');
const correctedText = document.getElementById('corrected-text');
const meta = document.getElementById('meta');

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    textarea.focus();
  });
});

correctBtn.addEventListener('click', correct);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') correct();
});

async function correct() {
  const text = textarea.value.trim();
  if (!text) return;

  correctBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/grammar', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: text
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const data = await res.json();
    renderResults(text, data);
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    correctBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderResults(input, data) {
  originalText.textContent = input;
  correctedText.textContent = data.corrected;
  meta.textContent = `${data.tokens} tokens in ${data.durationMs.toLocaleString()} ms`;
  results.classList.add('visible');
}
