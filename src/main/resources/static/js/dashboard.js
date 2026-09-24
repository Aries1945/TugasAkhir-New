// 1. Siapkan grafik. Paho bekerja di Java; Chart.js menggambar grafik di browser.
const tempatKartu = document.getElementById("sensor-cards-container");
const templateSensor = document.getElementById("sensor-template");
const warnaSensor = ["#0284c7", "#10b981", "#f59e0b", "#8b5cf6", "#e11d48"];
let grafik = null;

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
    const salinan = templateSensor.content.cloneNode(true);
    const terakhir = sensor.pembacaan[sensor.pembacaan.length - 1];
    const kartu = salinan.querySelector("article");
    kartu.style.setProperty("--sensor-color", warna);
    salinan.querySelector(".card-title").textContent = sensor.topic;
    salinan.querySelector(".gauge-value").textContent = terakhir.suhu.toLocaleString("id-ID", {
        minimumFractionDigits: 2, maximumFractionDigits: 2
    });
    salinan.querySelector(".sensor-time").textContent = formatWaktu(terakhir.waktu);
    salinan.querySelector(".sensor-time").dateTime = new Date(terakhir.waktu).toISOString();

    let status = "Data baru";
    if (!terhubung) status = "Broker terputus";
    else if (waktuServer - terakhir.waktu > 10000) status = "Belum ada data baru";
    salinan.querySelector(".sensor-freshness").textContent = status;

    // Lingkaran memakai skala 0–50 °C. Angka tetap menampilkan suhu sebenarnya.
    const persentase = Math.max(0, Math.min(1, terakhir.suhu / 50));
    salinan.querySelector(".gauge-value-path").style.strokeDashoffset = 188.5 * (1 - persentase);

    // Grafik kecil hanya menampilkan pembacaan dalam satu menit terakhir.
    const titik = sensor.pembacaan.filter(function (data) {
        return data.waktu >= waktuServer - 60000;
    });
    if (titik.length > 1) {
        const nilai = titik.map(function (data) { return data.suhu; });
        const minimum = Math.min(...nilai) - 0.5;
        const maksimum = Math.max(...nilai) + 0.5;
        const koordinat = titik.map(function (data) {
            const x = (data.waktu - (waktuServer - 60000)) / 60000 * 200;
            var y = 36 - (data.suhu - minimum) / (maksimum - minimum) * 32;
            return x + "," + y;
        });
        salinan.querySelector(".sparkline").setAttribute("points", koordinat.join(" "));
    }
    tempatKartu.append(salinan);
}

// =====================================================================
// 3. RENDER DATA KE DASHBOARD
//    Fungsi ini dipanggil setiap kali dapat data baru dari WebSocket.
//    Isinya sama persis seperti sebelumnya, hanya dipindah ke fungsi sendiri.
// =====================================================================
function perbaruiTampilan(data) {
    document.getElementById("mqtt-status").textContent = data.connected ? "Connected" : "Disconnected";
    document.getElementById("ws-indicator").style.backgroundColor = data.connected ? "#10b981" : "#94a3b8";
    document.getElementById("dashboard-empty").hidden = data.sensor.length > 0;
    document.getElementById("dashboard-info").textContent = data.connected
        ? "Terhubung ke broker · " + data.sensor.length + " sensor diterima"
        : "Broker terputus. Data sebelumnya tetap ditampilkan beserta waktu diterimanya.";

    tempatKartu.replaceChildren();
    const garisSensor = [];
    let nomor = 0;
    for (const sensor of data.sensor) {
        const warna = warnaSensor[nomor % warnaSensor.length];
        tampilkanKartu(sensor, warna, data.waktuServer, data.connected);
        const titikGrafik = [];
        for (const pembacaan of sensor.pembacaan) {
            if (pembacaan.waktu >= data.waktuServer - 60000) {
                titikGrafik.push({ x: pembacaan.waktu, y: pembacaan.suhu });
            }
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
        grafik.options.scales.x.min = data.waktuServer - 60000;
        grafik.options.scales.x.max = data.waktuServer;
        grafik.update("none");
    }
}

// =====================================================================
// 4. HUBUNGKAN WEBSOCKET
//
//    SEBELUM (polling):
//      Browser minta data setiap 1 detik → Server jawab → Tampilkan
//      Masalah: boros request, ada delay sampai 1 detik
//
//    SESUDAH (WebSocket):
//      Browser buka 1 koneksi tetap → Server PUSH data saat ada yang baru
//      Keuntungan: real-time, hemat bandwidth
//
//    Alur:
//    1. Browser buka WebSocket ke ws://localhost:8080/ws/suhu
//    2. Saat koneksi terbuka, ambil data awal via GET /api/suhu
//    3. Setelah itu, semua update datang otomatis via WebSocket
//    4. Jika koneksi putus, coba sambung ulang setiap 3 detik
// =====================================================================
function hubungkanWebSocket() {
    // Pilih protokol: ws:// untuk HTTP, wss:// untuk HTTPS
    const protokol = location.protocol === "https:" ? "wss:" : "ws:";
    const ws = new WebSocket(protokol + "//" + location.host + "/ws/suhu");

    // Saat koneksi WebSocket berhasil dibuka
    ws.onopen = function () {
        console.log("WebSocket terhubung ke server");

        // Ambil data awal via REST supaya dashboard langsung terisi
        // (tanpa harus menunggu pesan MQTT berikutnya)
        fetch("/api/suhu", { cache: "no-store" })
            .then(function (r) { return r.json(); })
            .then(perbaruiTampilan)
            .catch(function (e) { console.log("Gagal ambil data awal: " + e); });
    };

    // Saat server kirim data baru via WebSocket
    ws.onmessage = function (event) {
        try {
            const data = JSON.parse(event.data);
            perbaruiTampilan(data);
        } catch (e) {
            console.log("Gagal parse pesan WebSocket: " + e);
        }
    };

    // Saat terjadi error
    ws.onerror = function () {
        console.log("WebSocket error");
    };

    // Saat koneksi terputus → coba sambung ulang setelah 3 detik
    ws.onclose = function () {
        console.log("WebSocket terputus, reconnect dalam 3 detik...");
        document.getElementById("mqtt-status").textContent = "Reconnecting...";
        document.getElementById("ws-indicator").style.backgroundColor = "#f59e0b";
        setTimeout(hubungkanWebSocket, 3000);
    };
}

// Mulai hubungkan WebSocket saat halaman dimuat
hubungkanWebSocket();

