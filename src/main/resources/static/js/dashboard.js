// 1. Siapkan grafik. Paho bekerja di Java; Chart.js menggambar grafik di browser.
const tempatKartu = document.getElementById("sensor-cards-container");
const templateSensor = document.getElementById("sensor-template");
const warnaSensor = ["#0284c7", "#10b981", "#f59e0b", "#8b5cf6", "#e11d48"];
let grafik = null;
// Simpan kartu berdasarkan topic agar tidak dibuat ulang setiap pembaruan.
const kartuSensor = new Map();

// Tampilkan satu angka desimal agar suhu mudah dibaca.
function formatSuhu(suhu) {
    return suhu.toLocaleString("id-ID", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

function formatWaktu(waktu) {
    return new Date(waktu).toLocaleTimeString("id-ID", { hour12: false });
}

if (typeof Chart !== "undefined") {
    grafik = new Chart(document.getElementById("mainChart"), {
        type: "line",
        data: { datasets: [] },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            parsing: false,
            scales: {
                x: {
                    type: "linear",
                    ticks: { maxTicksLimit: 7, callback: formatWaktu },
                    grid: { color: "#f1f5f9" }
                },
                y: {
                    title: { display: true, text: "Suhu (°C)" },
                    grace: "10%",
                    grid: { color: "#f1f5f9" }
                }
            },
            plugins: {
                legend: { position: "bottom", labels: { boxWidth: 12, boxHeight: 3 } },
                tooltip: {
                    callbacks: {
                        title: function (items) { return formatWaktu(items[0].parsed.x); },
                        label: function (item) { return item.dataset.label + ": " + item.parsed.y.toFixed(2) + " °C"; }
                    }
                }
            }
        }
    });
} else {
    document.getElementById("chart-info").textContent = "Grafik gagal dimuat. Periksa koneksi internet lalu refresh. Angka suhu tetap tersedia.";
}

// 2. Isi kartu berdasarkan pembacaan terbaru milik satu sensor.
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
    kartu.querySelector(".gauge-value").textContent = formatSuhu(terakhir.suhu);
    kartu.querySelector(".sensor-time").textContent = formatWaktu(terakhir.waktu);
    kartu.querySelector(".sensor-time").dateTime = new Date(terakhir.waktu).toISOString();

    // Tampilkan peringatan hanya jika data terlambat atau broker terputus.
    let status = "";
    if (!terhubung) status = "Broker terputus";
    else if (waktuServer - terakhir.waktu > 10000) status = "Belum ada data baru";
    kartu.querySelector(".sensor-freshness").textContent = status;

    // Lingkaran memakai skala 0–50 °C. Angka tetap menampilkan suhu sebenarnya.
    const persentase = Math.max(0, Math.min(1, terakhir.suhu / 50));
    kartu.querySelector(".gauge-value-path").style.strokeDashoffset = 188.5 * (1 - persentase);

    // Grafik kecil memakai seluruh pembacaan yang masih tersimpan.
    const titik = sensor.pembacaan;
    // Cari suhu minimum dan maksimum dari seluruh pembacaan sensor ini.
    let minimum = titik[0].suhu;
    let maksimum = titik[0].suhu;
    for (const data of titik) {
        minimum = Math.min(minimum, data.suhu);
        maksimum = Math.max(maksimum, data.suhu);
    }
    kartu.querySelector(".sparkline").setAttribute("points", "");
    if (titik.length > 1) {
        // Beri ruang di atas dan bawah grafik kecil agar garis mudah dilihat.
        minimum -= 0.5;
        maksimum += 0.5;
        // Lebar grafik mengikuti waktu catatan pertama sampai terakhir.
        const waktuAwal = titik[0].waktu;
        const waktuAkhir = titik[titik.length - 1].waktu;
        const rentangWaktu = Math.max(1, waktuAkhir - waktuAwal);
        const koordinat = titik.map(function (data) {
            const x = (data.waktu - waktuAwal) / rentangWaktu * 200;
            var y = 36 - (data.suhu - minimum) / (maksimum - minimum) * 32;
            return x + "," + y;
        });
        kartu.querySelector(".sparkline").setAttribute("points", koordinat.join(" "));
    }
}

// =====================================================================
// 3. RENDER DATA KE DASHBOARD
//    Fungsi ini dipanggil setelah data REST API diterima.
// =====================================================================
function perbaruiTampilan(data) {
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
        }
    }
    const garisSensor = [];
    let nomor = 0;
    for (const sensor of data.sensor) {
        // Sensor tanpa pembacaan belum mempunyai angka yang bisa ditampilkan.
        if (sensor.pembacaan.length === 0) continue;
        const warna = warnaSensor[nomor % warnaSensor.length];
        tampilkanKartu(sensor, warna, data.waktuServer, data.connected);
        const titikGrafik = [];
        for (const pembacaan of sensor.pembacaan) {
            // Masukkan setiap pembacaan, termasuk yang lebih lama dari satu menit.
            titikGrafik.push({ x: pembacaan.waktu, y: pembacaan.suhu });
        }
        garisSensor.push({
            label: sensor.topic, data: titikGrafik,
            borderColor: warna, backgroundColor: warna,
            borderWidth: 2, pointRadius: 1.5, tension: 0
        });
        nomor++;
    }
    if (grafik) {
        grafik.data.datasets = garisSensor;
        // Chart.js menentukan rentang waktu dari seluruh titik yang diberikan.
        grafik.update("none");
    }
}

// 4. Periksa data baru dengan jeda seperempat detik agar grafik cepat diperbarui.
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

perbaruiDashboard();
