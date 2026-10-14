/**
 * ThermoStream History JavaScript
 */

// Simpan topic terpilih agar detail ikut diperbarui melalui REST API.
var selectedTopic = null;
var allSensorsData = []; // Cache for search
var filteredSensorsData = []; // Current filtered/displayed list
var detailChartInstance = null;

// Periksa respons HTTP dan hentikan permintaan yang terlalu lama.
async function readApi(url) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
        const response = await fetch(url, { cache: 'no-store', signal: controller.signal });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return await response.json();
    } finally {
        clearTimeout(timeout);
    }
}

function loadSensorsHistory() {
    return readApi('/api/sensor/sensors')
        .then(sensorList => {
            allSensorsData = sensorList;
            // Pertahankan pencarian saat data baru datang.
            const query = document.getElementById('search-input').value.toLowerCase();
            filteredSensorsData = sensorList.filter(sensor => sensor.topic.toLowerCase().includes(query));

            // Update sensor count summary text
            const summaryText = document.getElementById('sensor-count-summary');
            if (summaryText) {
                summaryText.textContent = `${sensorList.length} sensor terdaftar — klik untuk melihat detail.`;
            }

            // Tampilkan semua kartu yang cocok dengan pencarian, tanpa pembagian halaman.
            renderCards(filteredSensorsData);
        })
        .catch(err => {
            console.error('Error fetching sensors:', err);
            // Data terakhir tetap terlihat jika server tidak dapat diakses.
            document.getElementById('sensor-count-summary').textContent = 'Gagal memperbarui riwayat. Mencoba kembali...';
        });
}

function formatSensorName(topic) {
    if (!topic) return 'Sensor';
    const parts = topic.split('/');
    const last = parts[parts.length - 1];
    return last.charAt(0).toUpperCase() + last.slice(1);
}

function getSensorConfig(sensorId) {
    const colors = ['var(--temp-cold)', 'var(--temp-normal)', 'var(--temp-warm)'];
    let sum = 0;
    const str = sensorId || '';
    for (let i = 0; i < str.length; i++) sum += str.charCodeAt(i);
    const colorVal = colors[sum % colors.length];
    return { name: formatSensorName(sensorId), id: sensorId, location: sensorId, color: colorVal };
}


// Helper to resolve CSS variables
function getComputedColor(varName) {
    if (!varName.startsWith('var')) return varName;
    const matches = varName.match(/var\(([^)]+)\)/);
    const cleanName = matches ? matches[1] : varName;
    return getComputedStyle(document.documentElement).getPropertyValue(cleanName).trim() || '#0ea5e9';
}

function renderCards(sensorList) {
    const container = document.getElementById('sensor-list-container');
    container.innerHTML = '';

    if (!sensorList || sensorList.length === 0) {
        container.innerHTML = '<div class="glass" style="grid-column: 1 / -1; text-align: center; color: var(--text-muted); padding: 48px 0;">Belum ada data sensor.</div>';
        return;
    }

    sensorList.forEach(sensor => {
        const meta = getSensorConfig(sensor.topic);
        const val = sensor.suhu != null ? sensor.suhu.toFixed(1) : '--';
        const timeStr = sensor.waktu ? new Date(sensor.waktu).toLocaleString('id-ID') : 'belum ada data';

        // Check online status: online if received a message in the last 30 seconds
        const isOnline = sensor.waktu && (Date.now() - new Date(sensor.waktu).getTime() < 30000);

        // Resolve card theme color
        const colorHex = getComputedColor(meta.color);

        const card = document.createElement('div');
        card.className = 'gradient-card cursor-pointer group';
        card.onclick = () => loadSensorDetail(sensor.topic);

        card.innerHTML = `
            <!-- Top Right Arrow -->
            <div class="arrow-icon">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>
            </div>

            <!-- Header -->
            <div class="flex items-start space-x-4">
                <div class="card-header-icon">
                    <svg class="w-5 h-5 text-sky-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 002 2h2a2 2 0 002-2"></path></svg>
                </div>
                <div>
                    <h3 class="card-title"></h3>
                </div>
            </div>

            <!-- Bottom Section -->
            <div class="card-meta-text">
                <div>
                    <div class="card-meta-value" style="color: ${colorHex}">${val}<span class="card-meta-unit">°C</span></div>
                    <div class="card-meta-time">last: ${timeStr}</div>
                </div>
                <div class="card-meta-right">
                    <span class="status-pill ${isOnline ? 'online' : 'offline'}">
                        <div class="status-pill-dot"></div>
                        ${isOnline ? 'DATA BARU' : 'BELUM ADA DATA BARU'}
                    </span>
                </div>
            </div>
        `;

        // Nama topic adalah data MQTT; tampilkan sebagai teks, bukan HTML.
        card.querySelector('.card-title').textContent = meta.name;
        card.title = sensor.topic;
        container.appendChild(card);
    });
}

function loadSensorDetail(sensorId, scroll = true) {
    selectedTopic = sensorId;
    return readApi('/api/sensor/sensors/detail?topic=' + encodeURIComponent(sensorId))
        .then(detail => {
            // Abaikan jawaban lama jika pengguna sudah menutup atau memilih sensor lain.
            if (selectedTopic !== sensorId) return;
            renderSensorDetail(detail);

            // Scroll to detail view smoothly
            const detailSection = document.getElementById('detail-section');
            if (detailSection && scroll) {
                detailSection.scrollIntoView({ behavior: 'smooth', block: 'end' });
            }
        })
        .catch(err => {
            console.error(err);
            document.getElementById('sensor-count-summary').textContent = 'Detail gagal dimuat. Silakan coba lagi.';
        });
}

function renderSensorDetail(detail) {
    const section = document.getElementById('detail-section');
    const tbody = document.getElementById('detail-tbody');
    const title = document.getElementById('detail-title');

    if (!detail || !detail.recentData || detail.recentData.length === 0) {
        section.classList.add('hidden');
        if (detailChartInstance) {
            detailChartInstance.destroy();
            detailChartInstance = null;
        }
        return;
    }

    const meta = getSensorConfig(detail.topic);
    title.textContent = `Riwayat: ${meta.name}`;

    tbody.innerHTML = '';
    detail.recentData.forEach((item, index) => {
        const tr = document.createElement('tr');
        tr.className = 'tr';
        tr.innerHTML = `
            <td class="td font-medium text-[var(--text-primary)]">${index + 1}</td>
            <td class="td font-display font-semibold text-[var(--text-primary)] tabular-nums">${item.value.toFixed(2)} °C</td>
            <td class="td text-muted font-medium tabular-nums">${new Date(item.receivedAt).toLocaleString('id-ID')}</td>
        `;
        tbody.appendChild(tr);
    });

    // Render Chart
    // Tabel tetap tampil meskipun library grafik gagal dimuat.
    section.classList.remove('hidden');
    if (typeof Chart === 'undefined') return;
    const ctx = document.getElementById('detail-chart').getContext('2d');
    if (detailChartInstance) {
        detailChartInstance.destroy();
    }

    const chartData = [...detail.recentData].reverse();
    const labels = chartData.map(item => new Date(item.receivedAt).toLocaleTimeString('id-ID'));
    const values = chartData.map(item => item.value);

    const isDark = document.documentElement.classList.contains('dark');
    const gridColor = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.05)';
    const textColor = isDark ? '#94a3b8' : '#64748b';
    const lineColor = getComputedColor(meta.color) || '#0ea5e9';

    detailChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Suhu (°C)',
                data: values,
                borderColor: lineColor,
                backgroundColor: isDark ? 'rgba(14, 165, 233, 0.1)' : 'rgba(14, 165, 233, 0.05)',
                borderWidth: 2,
                tension: 0.4,
                pointRadius: 2,
                pointHoverRadius: 5,
                fill: true
            }]
        },
        options: {
            animation: false,
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: {
                        color: textColor,
                        font: { size: 10 }
                    }
                },
                y: {
                    grid: { color: gridColor },
                    ticks: {
                        color: textColor,
                        font: { size: 10 }
                    }
                }
            }
        }
    });

    section.classList.remove('hidden');
}

// Search functionality
document.getElementById('search-input').addEventListener('input', function (e) {
    const q = e.target.value.toLowerCase();
    filteredSensorsData = allSensorsData.filter(sensor => {
        const meta = getSensorConfig(sensor.topic);
        return meta.name.toLowerCase().includes(q) ||
            meta.location.toLowerCase().includes(q) ||
            sensor.topic.toLowerCase().includes(q);
    });
    // Tampilkan seluruh hasil pencarian sekaligus.
    renderCards(filteredSensorsData);
});

// Close detail
document.getElementById('close-detail').addEventListener('click', function () {
    selectedTopic = null;
    document.getElementById('detail-section').classList.add('hidden');
    if (detailChartInstance) {
        detailChartInstance.destroy();
        detailChartInstance = null;
    }
});

// Tunggu semua permintaan selesai, lalu beri jeda 1 detik sebelum pembaruan berikutnya.
async function refreshHistory() {
    await loadSensorsHistory();
    try {
        const status = await readApi('/api/mqtt/subscription-status');
        document.getElementById('mqtt-status').textContent = status.connected ? 'Terhubung' : status.reconnecting ? 'Menghubungkan kembali' : 'Terputus';
        document.getElementById('mqtt-indicator').style.backgroundColor = status.connected ? '#10b981' : '#94a3b8';
    } catch (error) {
        document.getElementById('mqtt-status').textContent = 'Status tidak tersedia';
        document.getElementById('mqtt-indicator').style.backgroundColor = '#94a3b8';
    }
    if (selectedTopic !== null) await loadSensorDetail(selectedTopic, false);
    setTimeout(refreshHistory, 1000);
}

document.addEventListener('DOMContentLoaded', refreshHistory);
