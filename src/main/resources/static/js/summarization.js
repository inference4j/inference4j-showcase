const textarea = document.getElementById('input-text');
const summarizeBtn = document.getElementById('summarize-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const modelSelect = document.getElementById('model-select');
const resultCard = document.getElementById('result-card');
const resultOutput = document.getElementById('result-output');

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    textarea.focus();
  });
});

summarizeBtn.addEventListener('click', summarize);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') summarize();
});

async function summarize() {
  const text = textarea.value.trim();
  if (!text) return;

  summarizeBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');

  // Show result card and clear previous output
  resultOutput.textContent = '';
  resultCard.style.display = '';

  // Remove any previous meta element
  const oldMeta = resultCard.querySelector('.generation-meta');
  if (oldMeta) oldMeta.remove();

  try {
    const res = await fetch('/api/summarization', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, model: modelSelect.value })
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let currentEvent = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.substring(5);
          handleEvent(currentEvent, data);
          currentEvent = '';
        }
      }
    }

    // Process remaining buffer
    if (buffer.trim()) {
      const lines = buffer.split('\n');
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.substring(5);
          handleEvent(currentEvent, data);
          currentEvent = '';
        }
      }
    }
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    summarizeBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function handleEvent(event, data) {
  if (event === 'token') {
    resultOutput.textContent += data;
    resultCard.scrollIntoView({ behavior: 'smooth', block: 'end' });
  } else if (event === 'done') {
    try {
      const meta = JSON.parse(data);
      const metaEl = document.createElement('div');
      metaEl.className = 'generation-meta';
      const seconds = (meta.durationMillis / 1000).toFixed(1);
      const tokPerSec = (meta.tokenCount / (meta.durationMillis / 1000)).toFixed(1);
      metaEl.textContent = `${meta.tokenCount} tokens in ${seconds}s (${tokPerSec} tok/s)`;
      resultCard.appendChild(metaEl);
    } catch (e) {
      // ignore parse errors
    }
  } else if (event === 'error') {
    error.textContent = data;
    error.classList.add('visible');
  }
}
