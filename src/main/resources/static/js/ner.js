const textarea = document.getElementById('input-text');
const recognizeBtn = document.getElementById('recognize-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const results = document.getElementById('results');
const annotatedText = document.getElementById('annotated-text');
const entitiesBody = document.getElementById('entities-body');

const LABEL_COLORS = {
  PER:  { bg: '#dbeafe', border: '#93c5fd', text: '#1e40af' },
  ORG:  { bg: '#fce7f3', border: '#f9a8d4', text: '#9d174d' },
  LOC:  { bg: '#d1fae5', border: '#6ee7b7', text: '#065f46' },
  MISC: { bg: '#fef3c7', border: '#fcd34d', text: '#92400e' }
};

const LABEL_NAMES = {
  PER: 'Person',
  ORG: 'Organization',
  LOC: 'Location',
  MISC: 'Miscellaneous'
};

function colorFor(label) {
  return LABEL_COLORS[label] || { bg: '#f3f4f6', border: '#d1d5db', text: '#374151' };
}

document.querySelectorAll('.example-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    textarea.value = btn.dataset.text;
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

  recognizeBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  results.classList.remove('visible');

  try {
    const res = await fetch('/api/ner', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: text
    });

    if (!res.ok) throw new Error(`Server error (${res.status})`);

    const entities = await res.json();
    renderResults(text, entities);
  } catch (err) {
    error.textContent = err.message;
    error.classList.add('visible');
  } finally {
    recognizeBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function renderResults(text, entities) {
  // Build annotated text with highlighted spans
  annotatedText.innerHTML = '';
  let cursor = 0;

  // Sort entities by start offset
  const sorted = [...entities].sort((a, b) => a.start - b.start);

  sorted.forEach(entity => {
    // Text before entity
    if (entity.start > cursor) {
      annotatedText.appendChild(
        document.createTextNode(text.substring(cursor, entity.start)));
    }

    // Entity span
    const color = colorFor(entity.label);
    const span = document.createElement('span');
    span.className = 'ner-entity';
    span.style.background = color.bg;
    span.style.borderColor = color.border;
    span.style.color = color.text;
    span.innerHTML = `${escapeHtml(entity.text)}<span class="ner-tag" style="background:${color.border};color:#fff">${entity.label}</span>`;
    annotatedText.appendChild(span);
    cursor = entity.end;
  });

  // Remaining text
  if (cursor < text.length) {
    annotatedText.appendChild(
      document.createTextNode(text.substring(cursor)));
  }

  // Build entity list
  entitiesBody.innerHTML = '';
  if (sorted.length === 0) {
    entitiesBody.innerHTML = '<p style="color:var(--text-muted);font-size:0.875rem;">No entities found.</p>';
  } else {
    sorted.forEach(entity => {
      const color = colorFor(entity.label);
      const pct = (entity.score * 100).toFixed(1);
      const row = document.createElement('div');
      row.className = 'ner-row';
      row.innerHTML = `
        <span class="ner-row-swatch" style="background:${color.border}"></span>
        <span class="ner-row-text">${escapeHtml(entity.text)}</span>
        <span class="ner-row-label">${LABEL_NAMES[entity.label] || entity.label}</span>
        <span class="ner-row-score">${pct}%</span>
      `;
      entitiesBody.appendChild(row);
    });
  }

  results.classList.add('visible');
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}
