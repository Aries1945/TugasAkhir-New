// Data berasal dari riwayat PostgreSQL melalui REST API, bukan koneksi MQTT baru.
const daftarKartu = document.getElementById("sensor-list-container");
const templateKartu = document.getElementById("history-sensor-template");
const bagianDetail = document.getElementById("detail-section");
const isiTabel = document.getElementById("detail-tbody");
const pesanRiwayat = document.getElementById("history-info");
let daftarSensor = [];
let topicDipilih = "";
let grafik = null;

// Tampilkan tanggal dan waktu sesuai zona waktu browser pengguna.
function formatWaktu(waktu) {
    return new Date(waktu).toLocaleString("id-ID", { hour12: false });
}

// Buat grafik satu kali. Jika library gagal dimuat, tabel tetap bisa digunakan.
function siapkanGrafik() {
    if (typeof Chart === "undefined") {
        document.getElementById("chart-info").textContent =
            "Grafik gagal dimuat. Seluruh catatan tetap tersedia di tabel.";
        return;
    }

    grafik = new Chart(document.getElementById("detail-chart"), {
        type: "line",
        data: { datasets: [{ label: "Suhu (°C)", data: [], borderColor: "#0284c7" }] },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            parsing: false,
            scales: {
                x: { type: "linear", ticks: { maxTicksLimit: 4, callback: formatWaktu } },
                y: { title: { display: true, text: "Suhu (°C)" } }
            },
            plugins: {
                tooltip: {
                    callbacks: {
                        // Sumbu waktu menyimpan milidetik; ubah menjadi tanggal yang terbaca.
                        title: function (items) { return formatWaktu(items[0].parsed.x); }
                    }
                }
            }
        }
    });
}

// Pertahankan tombol yang sudah ada agar fokus pengguna tidak hilang saat pembaruan.
function tampilkanSensor() {
    for (const kartu of Array.from(daftarKartu.children)) {
        const masihAda = daftarSensor.some(sensor => sensor.topic === kartu.dataset.topic);
        if (!masihAda) kartu.remove();
    }

    for (const sensor of daftarSensor) {
        let kartu = Array.from(daftarKartu.children).find(kartu => kartu.dataset.topic === sensor.topic);
        if (!kartu) {
            kartu = templateKartu.content.firstElementChild.cloneNode(true);
            kartu.dataset.topic = sensor.topic;
            kartu.querySelector(".card-title").textContent = sensor.topic;
            // Klik kartu hanya memilih data. Tidak melakukan subscribe ulang.
            kartu.addEventListener("click", function () {
                topicDipilih = sensor.topic;
                tampilkanSensor();
                tampilkanDetail();
            });
            daftarKartu.append(kartu);
        }
        kartu.querySelector(".history-count").textContent = sensor.pembacaan.length + " catatan";
        kartu.setAttribute("aria-pressed", String(sensor.topic === topicDipilih));
    }
}

// Buat satu sel dengan textContent agar isi data tidak dianggap sebagai kode HTML.
function tambahSel(baris, teks) {
    const sel = document.createElement("td");
    sel.className = "td";
    sel.textContent = teks;
    baris.append(sel);
}

// Tampilkan semua catatan sensor, termasuk catatan yang nilai suhunya sama.
function tampilkanDetail() {
    const sensor = daftarSensor.find(sensor => sensor.topic === topicDipilih);
    bagianDetail.hidden = !sensor;
    if (!sensor) {
        topicDipilih = "";
        return;
    }

    document.getElementById("detail-title").textContent = "Riwayat: " + sensor.topic;
    const barisBaru = document.createDocumentFragment();
    // Catatan terbaru berada di atas. Tidak ada pembatasan jumlah catatan.
    for (let nomor = sensor.pembacaan.length - 1; nomor >= 0; nomor--) {
        const catatan = sensor.pembacaan[nomor];
        const baris = document.createElement("tr");
        baris.className = "tr";
        tambahSel(baris, nomor + 1);
        tambahSel(baris, catatan.suhu);
        tambahSel(baris, formatWaktu(catatan.waktu));
        barisBaru.append(baris);
    }
    isiTabel.replaceChildren(barisBaru);

    // Grafik memakai urutan waktu dari catatan pertama sampai terakhir.
    if (grafik) {
        grafik.data.datasets[0].data = sensor.pembacaan.map(function (catatan) {
            return { x: catatan.waktu, y: catatan.suhu };
        });
        grafik.update();
    }
}

// Ambil riwayat terbaru. Tunggu selesai sebelum menjadwalkan permintaan berikutnya.
async function muatRiwayat() {
    const pembatal = new AbortController();
    const batasWaktu = setTimeout(() => pembatal.abort(), 5000);
    try {
        const respons = await fetch("/api/riwayat", { cache: "no-store", signal: pembatal.signal });
        if (!respons.ok) throw new Error("Data gagal diambil");
        const data = await respons.json();
        daftarSensor = data.sensor;
        document.getElementById("mqtt-status").textContent = data.connected
            ? "Terhubung" : data.reconnecting ? "Menghubungkan kembali" : "Terputus";
        document.getElementById("mqtt-indicator").style.backgroundColor = data.connected ? "#10b981" : "#94a3b8";
        pesanRiwayat.textContent = daftarSensor.length === 0
            ? "Belum ada riwayat. Hubungkan broker dan subscribe ke topic sensor."
            : "Riwayat diperbarui setiap 1 detik. Pilih sensor untuk melihat catatannya.";
        tampilkanSensor();
        tampilkanDetail();
    } catch (error) {
        // Jangan menghapus riwayat yang sudah tampil ketika REST API gagal diakses.
        pesanRiwayat.textContent = "Gagal memperbarui riwayat. Data terakhir tetap ditampilkan. Mencoba kembali...";
        document.getElementById("mqtt-status").textContent = "Status tidak tersedia";
        document.getElementById("mqtt-indicator").style.backgroundColor = "#94a3b8";
    } finally {
        clearTimeout(batasWaktu);
        setTimeout(muatRiwayat, 1000);
    }
}

// Menutup detail tidak menghapus riwayat dan tidak memutuskan koneksi MQTT.
document.getElementById("close-detail").addEventListener("click", function () {
    topicDipilih = "";
    bagianDetail.hidden = true;
    tampilkanSensor();
});

// Mulai mengambil data ketika halaman History dibuka.
siapkanGrafik();
muatRiwayat();
