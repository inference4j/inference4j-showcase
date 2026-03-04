const textarea = document.getElementById('input-text');
const generateBtn = document.getElementById('generate-btn');
const spinner = document.getElementById('spinner');
const error = document.getElementById('error');
const modelSelect = document.getElementById('model-select');
const sqlCard = document.getElementById('sql-card');
const sqlOutput = document.getElementById('sql-output');
const runBtn = document.getElementById('run-btn');
const runSpinner = document.getElementById('run-spinner');
const runError = document.getElementById('run-error');
const genMeta = document.getElementById('gen-meta');
const resultsCard = document.getElementById('results-card');
const resultsTable = document.getElementById('results-table');

let generatedSql = '';

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
runBtn.addEventListener('click', executeSql);

async function generate() {
  const question = textarea.value.trim();
  if (!question) return;

  generateBtn.disabled = true;
  spinner.classList.add('visible');
  error.classList.remove('visible');
  runError.classList.remove('visible');

  // Show SQL card and reset
  sqlOutput.textContent = '';
  sqlCard.style.display = '';
  runBtn.disabled = true;
  genMeta.style.display = 'none';
  resultsCard.style.display = 'none';
  generatedSql = '';

  try {
    const res = await fetch('/api/text2sql', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, model: modelSelect.value })
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
    generateBtn.disabled = false;
    spinner.classList.remove('visible');
  }
}

function handleEvent(event, data) {
  if (event === 'token') {
    sqlOutput.textContent += data;
    sqlCard.scrollIntoView({ behavior: 'smooth', block: 'end' });
  } else if (event === 'done') {
    try {
      const meta = JSON.parse(data);
      generatedSql = meta.sql;
      sqlOutput.textContent = meta.sql;
      runBtn.disabled = false;
      const seconds = (meta.durationMillis / 1000).toFixed(1);
      const tokPerSec = (meta.tokenCount / (meta.durationMillis / 1000)).toFixed(1);
      genMeta.textContent = `${meta.tokenCount} tokens in ${seconds}s (${tokPerSec} tok/s)`;
      genMeta.style.display = '';
    } catch (e) {
      // ignore parse errors
    }
  } else if (event === 'error') {
    error.textContent = data;
    error.classList.add('visible');
  }
}

async function executeSql() {
  if (!generatedSql) return;

  runBtn.disabled = true;
  runSpinner.classList.add('visible');
  runError.classList.remove('visible');
  resultsCard.style.display = 'none';

  try {
    const res = await fetch('/api/text2sql/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sql: generatedSql })
    });

    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `Server error (${res.status})`);
    }

    const rows = await res.json();
    renderTable(rows);
    resultsCard.style.display = '';
    resultsCard.scrollIntoView({ behavior: 'smooth', block: 'end' });
  } catch (err) {
    runError.textContent = err.message;
    runError.classList.add('visible');
  } finally {
    runBtn.disabled = false;
    runSpinner.classList.remove('visible');
  }
}

function renderTable(rows) {
  resultsTable.innerHTML = '';
  if (!rows || rows.length === 0) {
    resultsTable.innerHTML = '<tr><td>No results</td></tr>';
    return;
  }

  const columns = Object.keys(rows[0]);

  // Header
  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  columns.forEach(col => {
    const th = document.createElement('th');
    th.textContent = col;
    headerRow.appendChild(th);
  });
  thead.appendChild(headerRow);
  resultsTable.appendChild(thead);

  // Body
  const tbody = document.createElement('tbody');
  rows.forEach(row => {
    const tr = document.createElement('tr');
    columns.forEach(col => {
      const td = document.createElement('td');
      td.textContent = row[col] != null ? row[col] : '';
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
  resultsTable.appendChild(tbody);
}
