// Fills a <select> with the execution providers the server offers (CPU always; CoreML or CUDA when available).
async function loadAccelerators(select) {
  try {
    const res = await fetch('/api/accelerators');
    const devices = await res.json();
    select.innerHTML = '';
    for (const device of devices) {
      const option = document.createElement('option');
      option.value = device.id;
      option.textContent = device.label;
      select.appendChild(option);
    }
    select.disabled = devices.length < 2;
  } catch {
    select.innerHTML = '<option value="cpu">CPU</option>';
    select.disabled = true;
  }
}

// "CoreML · 236 ms · 56 regions"
function timingText(device, millis, extra) {
  const label = device.split(' (')[0];
  return [label, `${millis} ms`, extra].filter(Boolean).join(' · ');
}
