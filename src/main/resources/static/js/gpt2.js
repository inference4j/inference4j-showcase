const textarea = document.getElementById('input-text');
const generateBtn = document.getElementById('generate-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const messages = document.getElementById('messages');

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
    textarea.focus();
  });
});

generateBtn.addEventListener('click', generate);
textarea.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') generate();
});

async function generate() {
  const prompt = textarea.value.trim();
  if (!prompt) return;

  generateBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');

  // Add user message bubble
  const userBubble = document.createElement('div');
  userBubble.className = 'card message message-user';
  userBubble.textContent = prompt;
  messages.appendChild(userBubble);

  // Add assistant message bubble
  const assistantBubble = document.createElement('div');
  assistantBubble.className = 'card message message-assistant';
  const responseText = document.createElement('div');
  assistantBubble.appendChild(responseText);
  messages.appendChild(assistantBubble);

  // Auto-scroll
  assistantBubble.scrollIntoView({ behavior: 'smooth', block: 'end' });

  try {
    const res = await fetch('/api/gpt2', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt })
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
          handleEvent(currentEvent, data, responseText, assistantBubble);
          currentEvent = '';
        }
      }
    }

    // Process any remaining buffer
    if (buffer.trim()) {
      const lines = buffer.split('\n');
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.substring(5);
          handleEvent(currentEvent, data, responseText, assistantBubble);
          currentEvent = '';
        }
      }
    }
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    generateBtn.disabled = false;
    spinner.classList.remove('visible');
    textarea.value = '';
    textarea.focus();
  }
}

function handleEvent(event, data, responseText, bubble) {
  if (event === 'token') {
    responseText.textContent += data;
    bubble.scrollIntoView({ behavior: 'smooth', block: 'end' });
  } else if (event === 'done') {
    try {
      const meta = JSON.parse(data);
      const metaEl = document.createElement('div');
      metaEl.className = 'generation-meta';
      const seconds = (meta.durationMillis / 1000).toFixed(1);
      const tokPerSec = (meta.tokenCount / (meta.durationMillis / 1000)).toFixed(1);
      metaEl.textContent = `${meta.tokenCount} tokens in ${seconds}s (${tokPerSec} tok/s)`;
      bubble.appendChild(metaEl);
    } catch (e) {
      // ignore parse errors
    }
  } else if (event === 'error') {
    const errorEl = document.getElementById('error');
    errorEl.textContent = data;
    errorEl.classList.add('visible');
  }
}
