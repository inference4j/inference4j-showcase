const modelSelect = document.getElementById('model-select');
const startBtn = document.getElementById('start-btn');
const stopBtn = document.getElementById('stop-btn');
const spinner = document.getElementById('spinner');
const statusEl = document.getElementById('status');
const errorEl = document.getElementById('error');
const transcript = document.getElementById('transcript');
const placeholder = document.getElementById('placeholder');
const statsEl = document.getElementById('stats');
const clearBtn = document.getElementById('clear-btn');
const levelMeter = document.getElementById('level-meter');
const levelFill = document.getElementById('level-fill');

// About 100 ms of audio per WebSocket message
const SEND_SECONDS = 0.1;

// Collects microphone samples off the main thread and posts them in blocks
const WORKLET = `
class Capture extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0];
    if (input && input[0]) this.port.postMessage(input[0].slice(0));
    return true;
  }
}
registerProcessor('capture', Capture);
`;

let socket = null;
let audioContext = null;
let mediaStream = null;
let pending = [];
let pendingLength = 0;
let lines = new Map();
let latencies = [];

startBtn.addEventListener('click', start);
stopBtn.addEventListener('click', stop);
clearBtn.addEventListener('click', () => {
  lines.clear();
  latencies = [];
  transcript.innerHTML = '';
  transcript.appendChild(placeholder);
  placeholder.hidden = false;
  statsEl.textContent = '';
});

async function start() {
  hideError();
  startBtn.disabled = true;
  modelSelect.disabled = true;
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    });
    audioContext = new AudioContext();
    const sampleRate = audioContext.sampleRate;

    setStatus(`Loading Moonshine ${modelSelect.value}…`, true);
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    socket = new WebSocket(`${protocol}://${location.host}/ws/live-transcription?model=${modelSelect.value}&sampleRate=${sampleRate}`);
    socket.binaryType = 'arraybuffer';
    socket.onmessage = event => handleEvent(JSON.parse(event.data));
    socket.onerror = () => showError('Connection error');
    socket.onclose = () => {
      // Closed by the server or the network, not by stop()
      if (socket) {
        cleanup();
        setStatus('Disconnected', false);
      }
    };

    const url = URL.createObjectURL(new Blob([WORKLET], { type: 'application/javascript' }));
    await audioContext.audioWorklet.addModule(url);
    const source = audioContext.createMediaStreamSource(mediaStream);
    const capture = new AudioWorkletNode(audioContext, 'capture');
    capture.port.onmessage = event => onSamples(event.data, sampleRate);
    source.connect(capture);
  } catch (err) {
    showError(err.name === 'NotAllowedError' ? 'Microphone permission was denied' : err.message);
    cleanup();
  }
}

function onSamples(samples, sampleRate) {
  showLevel(samples);
  if (!socket || socket.readyState !== WebSocket.OPEN || !socket.ready) return;
  pending.push(samples);
  pendingLength += samples.length;
  if (pendingLength >= sampleRate * SEND_SECONDS) {
    const block = new Float32Array(pendingLength);
    let offset = 0;
    for (const part of pending) {
      block.set(part, offset);
      offset += part.length;
    }
    pending = [];
    pendingLength = 0;
    socket.send(block.buffer);
  }
}

function handleEvent(event) {
  switch (event.type) {
    case 'ready':
      socket.ready = true;
      setStatus('Listening — start talking', false);
      startBtn.hidden = true;
      stopBtn.hidden = false;
      levelMeter.hidden = false;
      break;
    case 'started':
    case 'changed':
    case 'completed':
      renderLine(event);
      if (event.type !== 'started') {
        latencies.push(event.latencyMs);
        updateStats();
      }
      break;
    case 'error':
      showError(event.message);
      break;
  }
}

function renderLine(event) {
  placeholder.hidden = true;
  let el = lines.get(event.id);
  if (!el) {
    el = document.createElement('div');
    el.className = 'live-line partial';
    el.innerHTML = '<span class="live-time"></span><span class="live-text"></span>';
    transcript.appendChild(el);
    lines.set(event.id, el);
  }
  el.querySelector('.live-time').textContent = formatTime(event.start);
  el.querySelector('.live-text').textContent = event.text || '…';
  if (event.type === 'completed') {
    el.classList.remove('partial');
    el.title = `${event.start.toFixed(2)}–${event.end.toFixed(2)} s`;
  }
  el.scrollIntoView({ block: 'nearest' });
}

function updateStats() {
  const sorted = [...latencies].sort((a, b) => a - b);
  const p50 = sorted[Math.floor(sorted.length / 2)];
  const completed = [...lines.values()].filter(el => !el.classList.contains('partial')).length;
  statsEl.textContent = `(${completed} lines · update latency p50 ${p50} ms, last ${latencies[latencies.length - 1]} ms)`;
}

function stop() {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send('stop');
    // Give the server a moment to send the completed line before closing
    setTimeout(() => {
      if (socket) socket.close();
      cleanup();
    }, 500);
  } else {
    cleanup();
  }
  stopAudio();
  setStatus('Stopped', false);
}

function stopAudio() {
  if (mediaStream) mediaStream.getTracks().forEach(track => track.stop());
  if (audioContext) audioContext.close();
  mediaStream = null;
  audioContext = null;
  pending = [];
  pendingLength = 0;
  levelMeter.hidden = true;
}

function cleanup() {
  stopAudio();
  socket = null;
  startBtn.hidden = false;
  startBtn.disabled = false;
  stopBtn.hidden = true;
  modelSelect.disabled = false;
  spinner.classList.remove('visible');
}

function showLevel(samples) {
  let sum = 0;
  for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
  const rms = Math.sqrt(sum / samples.length);
  levelFill.style.width = `${Math.min(100, rms * 400)}%`;
}

function formatTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

function setStatus(text, busy) {
  statusEl.textContent = text;
  spinner.classList.toggle('visible', busy);
}

function showError(msg) {
  errorEl.textContent = msg;
  errorEl.classList.add('visible');
  spinner.classList.remove('visible');
}

function hideError() {
  errorEl.classList.remove('visible');
}
