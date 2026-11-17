const $ = id => document.getElementById(id);

const example = document.body.dataset.example;
const api = `/api/rag/${example}`;
const isAudio = example === 'calls';

const processBtn = $('process-btn');
const fileList = $('file-list');
const filesSummary = $('files-summary');
const statusEl = $('status');
const statusText = $('status-text');
const processError = $('process-error');
const detail = $('detail');
const questionEl = $('question');
const askBtn = $('ask-btn');
const askError = $('ask-error');
const messages = $('messages');

let files = [];
let processed = [];
let busy = false;

// ------------------------------------------------------------------ SSE over fetch

/** POSTs and calls onEvent(name, data) for each server-sent event; data is parsed JSON. */
async function streamEvents(url, options, onEvent) {
  const res = await fetch(url, { method: 'POST', ...options });
  if (!res.ok) throw new Error(`Server error (${res.status})`);
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let boundary;
    while ((boundary = buffer.search(/\r?\n\r?\n/)) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary).replace(/^\r?\n\r?\n/, '');
      dispatch(block, onEvent);
    }
  }
  if (buffer.trim()) dispatch(buffer, onEvent);
}

function dispatch(block, onEvent) {
  let name = 'message';
  const data = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) name = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5));
  }
  if (!data.length) return;
  let payload;
  try { payload = JSON.parse(data.join('\n')); } catch (e) { payload = data.join('\n'); }
  onEvent(name, payload);
}

// ------------------------------------------------------------------ helpers

function clock(ms) {
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function fileUrl(name) {
  return `${api}/files/${encodeURIComponent(name)}`;
}

/** Renders text with [PLACEHOLDER] tokens highlighted. */
function renderRedacted(el, text) {
  el.innerHTML = '';
  for (const part of text.split(/(\[[A-Z][A-Z0-9_]*\])/g)) {
    if (/^\[[A-Z][A-Z0-9_]*\]$/.test(part)) {
      const mark = document.createElement('mark');
      mark.className = 'rag-redacted';
      mark.textContent = part;
      el.appendChild(mark);
    } else if (part) {
      el.appendChild(document.createTextNode(part));
    }
  }
}

const players = {};

/** Plays a recording from a moment, pausing any other recording. */
function playAt(name, startMs) {
  const audio = players[name];
  if (!audio) return;
  Object.values(players).forEach(other => { if (other !== audio) other.pause(); });
  audio.currentTime = (startMs || 0) / 1000;
  audio.play();
  audio.closest('.rag-file')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// ------------------------------------------------------------------ files

async function load() {
  const res = await fetch(api);
  const overview = await res.json();
  files = overview.files;
  processed = overview.processed;
  renderFiles();
}

function renderFiles() {
  fileList.innerHTML = '';
  for (const file of files) {
    const item = document.createElement('div');
    item.className = 'rag-file';
    const head = document.createElement('div');
    head.className = 'rag-file-head';
    head.innerHTML = '<strong></strong><span class="text-muted"></span>';
    head.querySelector('strong').textContent = file.label;
    const done = processed.find(p => p.name === file.name);
    head.querySelector('span').textContent = done
      ? (done.redactions ? `${done.redactions} redacted` : 'indexed')
      : '';
    item.appendChild(head);

    if (isAudio) {
      const audio = document.createElement('audio');
      audio.controls = true;
      audio.preload = 'none';
      audio.src = fileUrl(file.name);
      players[file.name] = audio;
      item.appendChild(audio);
    } else {
      const link = document.createElement('a');
      link.href = fileUrl(file.name);
      link.target = '_blank';
      const img = document.createElement('img');
      img.src = fileUrl(file.name);
      img.alt = file.label;
      img.loading = 'lazy';
      link.appendChild(img);
      item.appendChild(link);
    }

    if (done) {
      const show = document.createElement('button');
      show.type = 'button';
      show.className = 'link-btn';
      show.textContent = isAudio ? 'Show transcript' : 'Show OCR text';
      show.addEventListener('click', () => showDetail(done));
      item.appendChild(show);
    }
    fileList.appendChild(item);
  }

  const ready = processed.length === files.length && files.length > 0;
  const redactions = processed.reduce((sum, p) => sum + p.redactions, 0);
  filesSummary.textContent = ready
    ? `Indexed. ${redactions} personal data item${redactions === 1 ? '' : 's'} redacted before storing.`
    : 'Not processed yet. The first run downloads the models.';
  processBtn.textContent = ready ? 'Process again' : (isAudio ? 'Transcribe & index' : 'Read & index');
  askBtn.disabled = !ready;
  $('chat-empty') && ($('chat-empty').textContent = ready
    ? 'Ask a question, or pick an example below.'
    : (isAudio ? 'Transcribe the recordings, then ask a question.' : 'Read the forms, then ask a question.'));
}

function showDetail(p) {
  detail.innerHTML = '';
  const head = document.createElement('div');
  head.className = 'rag-detail-head';
  head.innerHTML = '<label></label><button type="button" class="link-btn">Close</button>';
  head.querySelector('label').textContent = p.label;
  head.querySelector('button').addEventListener('click', () => { detail.hidden = true; });
  detail.appendChild(head);

  const meta = document.createElement('p');
  meta.className = 'text-muted rag-detail-meta';
  const parts = [];
  if (p.details.segments != null) parts.push(`${p.details.segments} excerpts`);
  if (p.details.lines != null) parts.push(`${p.details.lines} lines, mean confidence ${(p.details.ocrConfidence * 100).toFixed(1)}%`);
  parts.push(`${isAudio ? 'transcribe' : 'OCR'} ${p.parseMillis} ms`, `redact ${p.redactMillis} ms`, `embed ${p.embedMillis} ms`);
  meta.textContent = parts.join(' · ');
  detail.appendChild(meta);

  const compare = document.createElement('div');
  compare.className = 'rag-compare';
  compare.appendChild(column(isAudio ? 'Transcript' : 'OCR text', p.original, p.name, false));
  compare.appendChild(column('Stored after redaction', p.redacted, p.name, true));
  detail.appendChild(compare);
  detail.hidden = false;
  detail.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function column(title, pieces, name, redacted) {
  const col = document.createElement('div');
  const label = document.createElement('span');
  label.className = 'rag-compare-label';
  label.textContent = title;
  col.appendChild(label);
  const box = document.createElement('div');
  box.className = 'rag-text';
  for (const piece of pieces) {
    const line = document.createElement('div');
    line.className = 'rag-piece';
    if (piece.startMs != null) {
      const time = document.createElement('button');
      time.type = 'button';
      time.className = 'rag-time';
      time.textContent = clock(piece.startMs);
      time.title = 'Play from here';
      time.addEventListener('click', () => playAt(name, piece.startMs));
      line.appendChild(time);
    }
    const text = document.createElement('span');
    if (redacted) renderRedacted(text, piece.text); else text.textContent = piece.text;
    line.appendChild(text);
    box.appendChild(line);
  }
  col.appendChild(box);
  return col;
}

processBtn.addEventListener('click', async () => {
  if (busy) return;
  busy = true;
  processBtn.disabled = true;
  askBtn.disabled = true;
  processError.classList.remove('visible');
  statusEl.hidden = false;
  statusText.textContent = 'Starting... (the first run downloads the models)';
  detail.hidden = true;
  processed = [];
  renderFiles();
  askBtn.disabled = true;
  try {
    await streamEvents(`${api}/process`, {}, (name, data) => {
      if (name === 'status') statusText.textContent = data.message + '...';
      if (name === 'file') { processed.push(data); renderFiles(); askBtn.disabled = true; }
      if (name === 'error') throw new Error(data.message);
    });
  } catch (e) {
    processError.textContent = e.message;
    processError.classList.add('visible');
  } finally {
    statusEl.hidden = true;
    busy = false;
    processBtn.disabled = false;
    renderFiles();
  }
});

// ------------------------------------------------------------------ pipeline panel

// AiServices order: retrieval and prompt building, then input guardrails, then the chat model
const STEPS = ['retrieval', 'prompt', 'guardrail', 'answer'].filter(name => $(`step-${name}`));
const after = name => STEPS[STEPS.indexOf(name) + 1];

function step(name) {
  const el = $(`step-${name}`);
  return {
    el,
    body: el.querySelector('.rag-step-body'),
    set(state, ms) {
      el.dataset.state = state;
      const msEl = el.querySelector('.rag-ms');
      if (msEl) msEl.textContent = ms != null ? `${ms} ms` : '';
    },
  };
}

function resetPipeline() {
  for (const name of STEPS) {
    step(name).set('idle');
    step(name).body.innerHTML = '';
  }
}

function sourceChip(passage) {
  const chip = document.createElement(isAudio ? 'button' : 'a');
  chip.className = 'rag-cite';
  if (isAudio) {
    chip.type = 'button';
    chip.textContent = `▶ ${passage.citation}`;
    chip.title = 'Play this moment';
    chip.addEventListener('click', () => playAt(passage.fileName, passage.startMs));
  } else {
    chip.textContent = passage.citation;
    chip.href = fileUrl(passage.fileName);
    chip.target = '_blank';
    chip.title = 'Open the scan';
  }
  return chip;
}

// ------------------------------------------------------------------ ask

function bubble(cls, text) {
  $('chat-empty')?.remove();
  const el = document.createElement('div');
  el.className = `card message ${cls}`;
  if (text) el.textContent = text;
  messages.appendChild(el);
  el.scrollIntoView({ behavior: 'smooth', block: 'end' });
  return el;
}

async function ask() {
  const question = questionEl.value.trim();
  if (!question || askBtn.disabled) return;
  askBtn.disabled = true;
  processBtn.disabled = true;
  askError.classList.remove('visible');
  resetPipeline();
  bubble('message-user', question);
  questionEl.value = '';

  const answer = bubble('message-assistant rag-answer');
  const answerText = document.createElement('div');
  answerText.className = 'rag-typing';
  answer.appendChild(answerText);
  let raw = '';
  let passages = [];

  step(STEPS[0]).set('running');
  try {
    await streamEvents(`${api}/ask`, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    }, (name, data) => {
      switch (name) {
        case 'guardrail': {
          const s = step('guardrail');
          s.set(data.passed ? 'done' : 'blocked', data.millis);
          s.body.textContent = data.passed ? 'Passed: no prompt injection detected.' : data.reason;
          if (data.passed) step('answer').set('running');
          break;
        }
        case 'blocked':
          step('answer').set('skipped');
          step('answer').body.textContent = 'The model was not called.';
          answer.classList.add('rag-answer--blocked');
          answerText.textContent = 'This message was blocked by the prompt-injection guardrail. The model was not called.';
          break;
        case 'retrieval': {
          passages = data.passages;
          const s = step('retrieval');
          s.set('done', data.millis);
          const list = document.createElement('div');
          list.className = 'rag-passages';
          for (const p of passages) {
            const row = document.createElement('div');
            row.className = 'rag-passage';
            const head = document.createElement('div');
            head.className = 'rag-passage-head';
            const score = document.createElement('span');
            score.className = 'rag-score';
            score.textContent = p.score.toFixed(3);
            const bar = document.createElement('span');
            bar.className = 'rag-bar';
            bar.style.setProperty('--w', `${Math.max(0, Math.min(1, p.score)) * 100}%`);
            score.prepend(bar);
            head.appendChild(score);
            head.appendChild(sourceChip(p));
            row.appendChild(head);
            const text = document.createElement('div');
            text.className = 'rag-passage-text';
            renderRedacted(text, p.text.replace(/\s+/g, ' '));
            row.appendChild(text);
            list.appendChild(row);
          }
          s.body.appendChild(list);
          step(after('retrieval')).set('running');
          break;
        }
        case 'prompt': {
          const s = step('prompt');
          s.set('done');
          const pre = document.createElement('div');
          pre.className = 'rag-prompt';
          renderRedacted(pre, `[system]\n${data.system}\n\n[user]\n${data.user}`);
          s.body.appendChild(pre);
          step(after('prompt')).set('running');
          break;
        }
        case 'token':
          raw += data.text;
          answerText.textContent = raw.trimStart();
          break;
        case 'answer': {
          const s = step('answer');
          s.set('done', data.millis);
          const rate = data.millis > 0 ? (data.tokens / (data.millis / 1000)).toFixed(1) : '–';
          s.body.textContent = `${data.tokens} tokens · ${rate} tokens/s`;
          if (passages.length) {
            const sources = document.createElement('div');
            sources.className = 'rag-answer-sources';
            const label = document.createElement('span');
            label.className = 'text-muted';
            label.textContent = 'Sources';
            sources.appendChild(label);
            passages.forEach(p => sources.appendChild(sourceChip(p)));
            answer.appendChild(sources);
          }
          break;
        }
        case 'error':
          throw new Error(data.message);
      }
    });
  } catch (e) {
    askError.textContent = e.message;
    askError.classList.add('visible');
    for (const name of STEPS) {
      if (step(name).el.dataset.state === 'running') step(name).set('error');
    }
    if (!raw && !answer.classList.contains('rag-answer--blocked')) answerText.textContent = 'Something went wrong.';
  } finally {
    answerText.classList.remove('rag-typing');
    askBtn.disabled = false;
    processBtn.disabled = false;
    answer.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }
}

askBtn.addEventListener('click', ask);
questionEl.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') ask();
});
document.querySelectorAll('.rag-examples .example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    if (askBtn.disabled) return;
    questionEl.value = btn.dataset.q;
    ask();
  });
});

load();
