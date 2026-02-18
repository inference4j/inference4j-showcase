const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('image-input');
const browseBtn = document.getElementById('browse-btn');
const clearBtn = document.getElementById('clear-btn');
const preview = document.getElementById('preview');
const promptInput = document.getElementById('prompt-input');
const generateBtn = document.getElementById('generate-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const messages = document.getElementById('messages');

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
  if (file && file.type.startsWith('image/')) selectFile(file);
});

clearBtn.addEventListener('click', e => {
  e.stopPropagation();
  clearSelection();
});

promptInput.addEventListener('input', updateGenerateState);
promptInput.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') generate();
});

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    promptInput.value = btn.dataset.prompt;
    updateGenerateState();
    promptInput.focus();
  });
});

function selectFile(file) {
  if (file.size > MAX_SIZE) {
    showError('Image exceeds 4 MB limit');
    return;
  }
  selectedFile = file;
  const url = URL.createObjectURL(file);
  preview.src = url;
  preview.hidden = false;
  clearBtn.hidden = false;
  dropZone.querySelector('.drop-zone-text').hidden = true;
  error.classList.remove('visible');
  updateGenerateState();
}

function clearSelection() {
  selectedFile = null;
  fileInput.value = '';
  preview.hidden = true;
  preview.src = '';
  clearBtn.hidden = true;
  dropZone.querySelector('.drop-zone-text').hidden = false;
  updateGenerateState();
}

function updateGenerateState() {
  const hasPrompt = promptInput.value.trim().length > 0;
  generateBtn.disabled = !selectedFile || !hasPrompt;
}

function showError(msg) {
  error.textContent = msg;
  error.classList.add('visible');
}

generateBtn.addEventListener('click', generate);

async function generate() {
  if (!selectedFile) return;
  const prompt = promptInput.value.trim();
  if (!prompt) return;

  generateBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');

  // Add user message bubble with image thumbnail and prompt
  const userBubble = document.createElement('div');
  userBubble.className = 'card message message-user';
  const thumb = document.createElement('img');
  thumb.src = preview.src;
  thumb.className = 'message-thumb';
  userBubble.appendChild(thumb);
  const promptText = document.createElement('div');
  promptText.textContent = prompt;
  userBubble.appendChild(promptText);
  messages.appendChild(userBubble);

  // Add assistant message bubble
  const assistantBubble = document.createElement('div');
  assistantBubble.className = 'card message message-assistant';
  const responseText = document.createElement('div');
  assistantBubble.appendChild(responseText);
  messages.appendChild(assistantBubble);

  assistantBubble.scrollIntoView({ behavior: 'smooth', block: 'end' });

  try {
    const form = new FormData();
    form.append('image', selectedFile);
    form.append('prompt', prompt);

    const res = await fetch('/api/vision-language', {
      method: 'POST',
      body: form
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

    // Process remaining buffer
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
    showError(err.message);
  } finally {
    updateGenerateState();
    spinner.classList.remove('visible');
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
    showError(data);
  }
}
