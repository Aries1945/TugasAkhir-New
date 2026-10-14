const tombolConnect = document.getElementById("btn-connect");
const statusKoneksi = document.getElementById("status");
let sedangMemproses = false;

tombolConnect.addEventListener("click", () => {
    if (sedangMemproses || tombolConnect.disabled) return;
    return tombolConnect.dataset.connected === "true" ? DisconnectBroker() : ConnectBroker();
});

function tampilkanStatusKoneksi(terhubung) {
    tombolConnect.dataset.connected = String(terhubung);
    tombolConnect.textContent = terhubung ? "Disconnect" : "Connect";
    tombolConnect.classList.toggle("btn-primary", !terhubung);
    tombolConnect.classList.toggle("btn-danger-outline", terhubung);
    tombolConnect.disabled = false;
    statusKoneksi.textContent = terhubung ? "Terhubung" : "Belum terhubung";
    kunciField(terhubung);
}

// Periksa input satu per satu sebelum menghubungi broker.
function periksaInput() {
    const host = document.getElementById("host").value.trim();
    const port = document.getElementById("port").value;
    const clientId = document.getElementById("clientId").value;
    const tls = document.getElementById("tls").value;

    if (host === "") {
        alert("Host broker harus diisi.");
        return false;
    }

    if (host.includes("://")) {
        alert("Host tidak perlu tcp:// atau ssl://. Contoh: broker.emqx.io");
        return false;
    }

    if (host.includes(" ")) {
        alert("Host broker tidak boleh mengandung spasi.");
        return false;
    }

    if (host.includes("/")) {
        alert("Isi nama broker saja, tanpa path. Contoh: broker.emqx.io");
        return false;
    }

    if (port === "") {
        alert("Port harus diisi dengan angka.");
        return false;
    }

    // Ubah teks input menjadi angka agar bisa diperiksa.
    const angkaPort = Number(port);

    if (Number.isInteger(angkaPort) === false) {
        alert("Port harus berupa angka bulat, bukan desimal.");
        return false;
    }

    if (angkaPort < 1 || angkaPort > 65535) {
        alert("Port harus antara 1 dan 65535.");
        return false;
    }

    if (clientId.trim() === "") {
        alert("Client ID harus diisi dan tidak boleh hanya spasi.");
        return false;
    }

    if (tls === "") {
        alert("Pilih Tanpa TLS atau Gunakan TLS/SSL.");
        return false;
    }

    // Semua pemeriksaan berhasil. Boleh lanjut Connect.
    return true;
}

// Jangan tampilkan JSON atau rincian error internal server sebagai alert.
async function bacaPesan(jawaban) {
    const tipe = jawaban.headers.get("content-type") || "";
    if (tipe.includes("application/json")) {
        return "Permintaan gagal diproses server. Periksa input atau log aplikasi Java.";
    }
    if (!tipe.includes("text/plain")) {
        return "Jawaban server tidak sesuai. Pastikan aplikasi Java sedang berjalan.";
    }
    return await jawaban.text();
}

async function ConnectBroker() {
    if (!periksaInput()) return;
    const konfigurasi = {};
    for (const nama of ["host", "port", "clientId", "username", "tls"]) {
        konfigurasi[nama] = document.getElementById(nama).value;
    }
    konfigurasi.host = konfigurasi.host.trim();
    const data = new URLSearchParams(konfigurasi);
    data.append("password", document.getElementById("password").value);
    await ubahKoneksi("connect", data, konfigurasi);
}

async function DisconnectBroker() {
    await ubahKoneksi("disconnect");
}

async function ubahKoneksi(aksi, data, konfigurasi) {
    if (sedangMemproses) return;
    sedangMemproses = true;
    tombolConnect.disabled = true;
    tombolConnect.textContent = aksi === "connect" ? "Menghubungkan..." : "Memutus koneksi...";
    document.getElementById("btn-subscribe").disabled = true;
    kunciField(true);
    try {
        const jawaban = await fetch("/api/mqtt/" + aksi, { method: "POST", body: data });
        if (jawaban.ok && konfigurasi) {
            try {
                sessionStorage.setItem("konfigurasiBroker", JSON.stringify(konfigurasi));
            } catch (error) {
                console.warn("Konfigurasi tidak dapat disimpan di browser.");
            }
        }
        alert(await bacaPesan(jawaban));
    } catch (error) {
        alert("Tidak dapat menghubungi server aplikasi.");
    } finally {
        await cekStatusSubscription();
        sedangMemproses = false;
    }
}

async function cekStatusKoneksi() {
    if (sedangMemproses) return;
    sedangMemproses = true;
    tombolConnect.disabled = true;
    kunciField(true);
    statusKoneksi.textContent = "Memeriksa koneksi...";
    try {
        const konfigurasi = sessionStorage.getItem("konfigurasiBroker");
        if (konfigurasi) {
            const data = JSON.parse(konfigurasi);
            for (const nama of ["host", "port", "clientId", "username", "tls"]) {
                document.getElementById(nama).value = data[nama] ?? "";
            }
        }
    } catch (error) {
        console.warn("Konfigurasi tersimpan tidak dapat dibaca.");
    }
    try {
        await cekStatusSubscription();
    } finally {
        sedangMemproses = false;
    }
}

function kunciField(terhubung) {
    document.querySelectorAll("#mqtt-config-form input, #mqtt-config-form select").forEach(field => {
        if (terhubung && document.activeElement === field) field.blur();
        field.disabled = terhubung;
    });
}

window.addEventListener("pageshow", cekStatusKoneksi);