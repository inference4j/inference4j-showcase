// Shared rendering for entity demos (NER, PII): highlighted text and an entity list.

const FIXED_COLORS = {
  PER:  { bg: '#dbeafe', border: '#93c5fd', text: '#1e40af' },
  ORG:  { bg: '#fce7f3', border: '#f9a8d4', text: '#9d174d' },
  LOC:  { bg: '#d1fae5', border: '#6ee7b7', text: '#065f46' },
  MISC: { bg: '#fef3c7', border: '#fcd34d', text: '#92400e' }
};

// Labels typed by the user get a color from this palette, the same one each time they appear
const PALETTE = [
  { bg: '#dbeafe', border: '#93c5fd', text: '#1e40af' },
  { bg: '#fce7f3', border: '#f9a8d4', text: '#9d174d' },
  { bg: '#d1fae5', border: '#6ee7b7', text: '#065f46' },
  { bg: '#fef3c7', border: '#fcd34d', text: '#92400e' },
  { bg: '#ede9fe', border: '#c4b5fd', text: '#5b21b6' },
  { bg: '#ffedd5', border: '#fdba74', text: '#9a3412' },
  { bg: '#cffafe', border: '#67e8f9', text: '#155e75' },
  { bg: '#fee2e2', border: '#fca5a5', text: '#991b1b' },
  { bg: '#ecfccb', border: '#bef264', text: '#3f6212' },
  { bg: '#f3f4f6', border: '#9ca3af', text: '#374151' }
];

const assigned = new Map();

function entityColor(label) {
  if (FIXED_COLORS[label]) return FIXED_COLORS[label];
  const key = label.toLowerCase();
  if (!assigned.has(key)) assigned.set(key, PALETTE[assigned.size % PALETTE.length]);
  return assigned.get(key);
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function renderAnnotated(container, text, entities) {
  container.innerHTML = '';
  let cursor = 0;
  [...entities].sort((a, b) => a.start - b.start).forEach(entity => {
    if (entity.start > cursor) {
      container.appendChild(document.createTextNode(text.substring(cursor, entity.start)));
    }
    const color = entityColor(entity.label);
    const span = document.createElement('span');
    span.className = 'ner-entity';
    span.style.background = color.bg;
    span.style.borderColor = color.border;
    span.style.color = color.text;
    span.innerHTML = `${escapeHtml(entity.text)}<span class="ner-tag" style="background:${color.border};color:#fff">${escapeHtml(entity.label)}</span>`;
    container.appendChild(span);
    cursor = entity.end;
  });
  if (cursor < text.length) {
    container.appendChild(document.createTextNode(text.substring(cursor)));
  }
}

function renderEntityList(container, entities, labelName = l => l) {
  container.innerHTML = '';
  if (entities.length === 0) {
    container.innerHTML = '<p style="color:var(--text-muted);font-size:0.875rem;">No entities found.</p>';
    return;
  }
  [...entities].sort((a, b) => a.start - b.start).forEach(entity => {
    const color = entityColor(entity.label);
    const row = document.createElement('div');
    row.className = 'ner-row';
    row.innerHTML = `
      <span class="ner-row-swatch" style="background:${color.border}"></span>
      <span class="ner-row-text">${escapeHtml(entity.text)}</span>
      <span class="ner-row-label">${escapeHtml(labelName(entity.label))}</span>
      <span class="ner-row-score">${(entity.score * 100).toFixed(1)}%</span>
    `;
    container.appendChild(row);
  });
}
