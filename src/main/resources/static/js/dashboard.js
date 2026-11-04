// 1. Siapkan grafik. Paho bekerja di Java; Chart.js menggambar grafik di browser.
const tempatKartu = document.getElementById("sensor-cards-container");
const templateSensor = document.getElementById("sensor-template");
const warnaSensor = ["#0284c7", "#10b981", "#f59e0b", "#8b5cf6", "#e11d48"];
// Simpan kartu berdasarkan topic agar tidak dibuat ulang setiap pembaruan.
const kartuSensor = new Map();
let sensorTerpilih = null;
let grafikDetail = null;

// Tampilkan satu angka desimal agar suhu mudah dibaca.
function formatSuhu(suhu) {
    return suhu.toLocaleString("id-ID", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

function formatWaktu(waktu) {
    return new Date(waktu).toLocaleTimeString("id-ID", { hour12: false });
}

// 1. Isi kartu pemilih sensor. Detailnya ditampilkan di bawah setelah kartu diklik.
function tampilkanKartu(sensor, warna, waktuServer, terhubung) {
    const terakhir = sensor.pembacaan[sensor.pembacaan.length - 1];
    // Buat kartu hanya saat topic ini pertama kali diterima.
    let kartu = kartuSensor.get(sensor.topic);
    if (!kartu) {
        kartu = templateSensor.content.querySelector("article").cloneNode(true);
        kartuSensor.set(sensor.topic, kartu);
        tempatKartu.append(kartu);
    }
    kartu.style.setProperty("--sensor-color", warna);
    kartu.querySelector(".card-title").textContent = sensor.topic;
    kartu.querySelector(".card-temperature-value").textContent = formatSuhu(terakhir.suhu);
    kartu.classList.toggle("selected", sensor.topic === sensorTerpilih);
    kartu.onclick = () => pilihSensor(sensor.topic);
    kartu.onkeydown = event => {
        if (event.key === "Enter" || event.key === " ") { event.preventDefault(); pilihSensor(sensor.topic); }
    };
}

function pilihSensor(topic) {
    sensorTerpilih = topic;
    for (const [namaTopic, kartu] of kartuSensor) {
        kartu.classList.toggle("selected", namaTopic === topic);
    }
    document.getElementById("sensor-detail").hidden = false;
    perbaruiDetail(window.dataDashboardTerakhir);
    document.getElementById("sensor-detail").scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function perbaruiDetail(data) {
    if (!data || !sensorTerpilih) return;
    const sensor = data.sensor.find(item => item.topic === sensorTerpilih && item.pembacaan.length > 0);
    if (!sensor) return;
    const titik = sensor.pembacaan;
    const suhu = titik.map(item => item.suhu);
    const minimum = Math.min(...suhu);
    const maksimum = Math.max(...suhu);
    document.getElementById("detail-sensor-name").textContent = sensor.topic;
    document.getElementById("detail-range").textContent = "Rentang suhu: " + formatSuhu(minimum) + "–" + formatSuhu(maksimum) + " °C · selisih " + formatSuhu(maksimum - minimum) + " °C";
    if (typeof Chart === "undefined") return;
    const warna = warnaSensor[data.sensor.indexOf(sensor) % warnaSensor.length];
    if (!grafikDetail) {
        grafikDetail = new Chart(document.getElementById("detailChart"), {
            type: "line", data: { datasets: [{ data: [], borderWidth: 2, pointRadius: 2, tension: 0.25, fill: true }] },
            options: { responsive: true, maintainAspectRatio: false, animation: false, parsing: false,
                scales: { x: { type: "linear", ticks: { maxTicksLimit: 7, callback: formatWaktu }, grid: { color: "#f1f5f9" } }, y: { title: { display: true, text: "Suhu (°C)" }, grace: "10%", grid: { color: "#f1f5f9" } } },
                plugins: { legend: { display: false }, tooltip: { callbacks: { title: items => formatWaktu(items[0].parsed.x), label: item => item.parsed.y.toFixed(2) + " °C" } } } }
        });
    }
    grafikDetail.data.datasets[0].data = titik.map(item => ({ x: item.waktu, y: item.suhu }));
    grafikDetail.data.datasets[0].borderColor = warna;
    grafikDetail.data.datasets[0].backgroundColor = warna + "1a";
    grafikDetail.update("none");
}

// =====================================================================
// 3. RENDER DATA KE DASHBOARD
//    Fungsi ini dipanggil setelah data REST API diterima.
// =====================================================================
function perbaruiTampilan(data) {
    window.dataDashboardTerakhir = data;
    document.getElementById("mqtt-status").textContent = data.connected ? "Connected" : "Disconnected";
    if (data.reconnecting) document.getElementById("mqtt-status").textContent = "Menghubungkan kembali...";
    document.getElementById("mqtt-indicator").style.backgroundColor = data.connected ? "#10b981" : "#94a3b8";
    document.getElementById("dashboard-empty").hidden = data.sensor.length > 0;
    document.getElementById("dashboard-info").textContent = data.connected && data.subscribed
        ? "Terhubung ke broker · " + data.sensor.length + " sensor diterima"
        : data.message + (data.connected ? " Buka Configure untuk mengatur subscription." : "");

    // Hapus kartu yang sudah tidak ada, misalnya setelah koneksi baru dimulai.
    for (const [topic, kartu] of kartuSensor) {
        if (!data.sensor.some(sensor => sensor.topic === topic && sensor.pembacaan.length > 0)) {
            kartu.remove();
            kartuSensor.delete(topic);
            if (sensorTerpilih === topic) sensorTerpilih = null;
        }
    }
    let nomor = 0;
    for (const sensor of data.sensor) {
        // Sensor tanpa pembacaan belum mempunyai angka yang bisa ditampilkan.
        if (sensor.pembacaan.length === 0) continue;
        const warna = warnaSensor[nomor % warnaSensor.length];
        tampilkanKartu(sensor, warna, data.waktuServer, data.connected);
        nomor++;
    }
    if (sensorTerpilih) perbaruiDetail(data);
}

// 3. Periksa data baru dengan jeda seperempat detik agar grafik cepat diperbarui.
async function perbaruiDashboard() {
    // Batalkan permintaan jika server tidak menjawab selama lima detik.
    const pembatal = new AbortController();
    const batasWaktu = setTimeout(() => pembatal.abort(), 5000);
    try {
        // Selalu ambil data terbaru. Seluruh pembacaan tetap ikut dikirim.
        const jawaban = await fetch("/api/suhu", {
            cache: "no-store",
            signal: pembatal.signal
        });
        if (!jawaban.ok) throw new Error("Gagal mengambil data suhu");
        perbaruiTampilan(await jawaban.json());
    } catch (error) {
        document.getElementById("mqtt-status").textContent = "Status tidak diketahui";
        document.getElementById("mqtt-indicator").style.backgroundColor = "#94a3b8";
        document.getElementById("dashboard-info").textContent = "Server belum dapat dihubungi. Data yang terlihat mungkin sudah lama.";
        for (const status of tempatKartu.querySelectorAll(".sensor-freshness")) {
            status.textContent = "Pembaruan terhenti";
        }
    } finally {
        // Hapus timer pembatalan setelah permintaan selesai atau gagal.
        clearTimeout(batasWaktu);
        // Mulai permintaan berikutnya setelah yang sekarang selesai, agar tidak bertumpuk.
        setTimeout(perbaruiDashboard, 250);
    }
}

document.getElementById("close-detail").addEventListener("click", () => {
    sensorTerpilih = null;
    document.getElementById("sensor-detail").hidden = true;
    for (const kartu of kartuSensor.values()) kartu.classList.remove("selected");
});

perbaruiDashboard();
