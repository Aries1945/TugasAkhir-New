// untuk button connect ke broker
const tombolConnect = document.getElementById("btn-connect");
const statusKoneksi = document.getElementById("status");


tombolConnect.addEventListener("click",ConnectBroker);


//untuk button disconnect
const tombolDisconnect = document.getElementById("btn-disconnect");

tombolDisconnect.addEventListener("click", DisconnectBroker);

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

//function untuk konek ke broker 
async function ConnectBroker() {

    //cek apakah input wajib di isi
    if(!periksaInput()){
        return null;
    }

    // input user
    const host = document.getElementById("host").value.trim();
    const port = document.getElementById("port").value;
    const clientId = document.getElementById("clientId").value;
    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;
    const tls = document.getElementById("tls").value;



     // 3. Masukkan input ke dalam data yang akan dikirim.
    const data = new URLSearchParams();

    data.append("host", host);
    data.append("port", port);
    data.append("clientId", clientId);
    data.append("username", username);
    data.append("password", password);
    data.append("tls", tls);

    // 4. Matikan tombol sementara agar tidak diklik berulang.
    tombolConnect.disabled = true;
    tombolConnect.textContent = "Menghubungkan...";
    document.getElementById("btn-subscribe").disabled = true;
    kunciField(true);

    let berhasilTerhubung = false;

    try {
        // 5. Kirim data ke controller Java.
        const jawaban = await fetch("/api/mqtt/connect", {
            method: "POST",
            body: data
        });

        // 6. Baca pesan yang dikirim controller.
        const pesan = await bacaPesan(jawaban);

        // 7. Jika berhasil, perbarui tulisan status.
        if (jawaban.ok) {
            berhasilTerhubung = true;
            statusKoneksi.textContent = "Terhubung";
            tombolConnect.textContent = "Connected";
            tombolConnect.disabled = true;
            tombolDisconnect.disabled = false;
            document.getElementById("btn-subscribe").disabled = false;

            // Simpan konfigurasi supaya tidak hilang saat refresh.
            try {
                sessionStorage.setItem("konfigurasiBroker", JSON.stringify({
                host,
                port,
                clientId,
                username,
                tls
                }));
            } catch (error) {
                console.warn("Konfigurasi tidak dapat disimpan di browser.");
            }

            kunciField(true);
        }

        // Tampilkan pesan berhasil atau gagal dari controller.
        alert(pesan);

    } catch (error) {
        // Browser tidak berhasil mendapatkan jawaban server.
        alert("Tidak dapat menghubungi server aplikasi.");

    } finally {
        // Aktifkan kembali tombol hanya jika koneksi gagal.
        if (berhasilTerhubung === false) {
            tombolConnect.disabled = false;
            tombolConnect.textContent = "Connect";
            kunciField(false);
        }
    }
}

async function DisconnectBroker() {

    // Cegah klik berulang selama proses berlangsung.
    tombolDisconnect.disabled = true;
    tombolDisconnect.textContent = "Memutus koneksi...";
    document.getElementById("btn-unsubscribe").disabled = true;
    document.getElementById("btn-subscribe").disabled = true;

    let berhasilDiputus = false;

    try {
        // Kirim permintaan ke controller.
        const jawaban = await fetch("/api/mqtt/disconnect", {
            method: "POST"
        });

        const pesan = await bacaPesan(jawaban);

        if (jawaban.ok) {
            berhasilDiputus = true;
            document.getElementById("mqtt-topic").disabled = false;
            document.getElementById("mqtt-qos").disabled = false;
            document.getElementById("btn-subscribe").dataset.subscribed = "false";
            document.getElementById("btn-subscribe").textContent = "Subscribe";

            statusKoneksi.textContent = "Belum terhubung";
            tombolConnect.disabled = false;
            tombolConnect.textContent = "Connect";
            kunciField(false);
        }

        alert(pesan);

    } catch (error) {
        alert("Tidak dapat menghubungi server aplikasi.");

    } finally {
        tombolDisconnect.textContent = "Disconnect";

        // Jika gagal, pengguna boleh mencoba Disconnect lagi.
        if (berhasilDiputus === false) {
            await cekStatusKoneksi();
        }
    }


    
}


async function cekStatusKoneksi() {
    document.getElementById("btn-unsubscribe").disabled = true;
    kunciField(true);
    document.getElementById("btn-subscribe").disabled = true;
    tombolConnect.disabled = true;
    tombolDisconnect.disabled = true;
    statusKoneksi.textContent = "Memeriksa koneksi...";
    try {
        const jawaban = await fetch("/api/mqtt/status", {
            cache: "no-store"
        });

        if (!jawaban.ok) {
            throw new Error("Gagal membaca status");
        }

        const terhubung = await jawaban.json();
        if (typeof terhubung !== "boolean") {
            throw new Error("Status koneksi tidak valid");
        }

        // Penyimpanan browser bisa tidak tersedia; status tetap harus diperbarui.
        try {
        const konfigurasi = sessionStorage.getItem("konfigurasiBroker");
        if (konfigurasi) {
            const data = JSON.parse(konfigurasi);

            document.getElementById("host").value = data.host;
            document.getElementById("port").value = data.port;
            document.getElementById("clientId").value = data.clientId;
            document.getElementById("username").value = data.username;
            document.getElementById("tls").value = data.tls;
        }
        } catch (error) {
            console.warn("Konfigurasi tersimpan tidak dapat dibaca.");
        }

        kunciField(terhubung);

        statusKoneksi.textContent = terhubung
            ? "Terhubung"
            : "Belum terhubung";

        tombolConnect.textContent = terhubung
            ? "Connected"
            : "Connect";

        tombolConnect.disabled = terhubung;
        tombolDisconnect.disabled = !terhubung;
        const subscribeButton = document.getElementById("btn-subscribe");
        if (!terhubung) {
            subscribeButton.dataset.subscribed = "false";
            subscribeButton.textContent = "Subscribe";
        }
        subscribeButton.disabled = !terhubung || subscribeButton.dataset.subscribed === "true";
        const subscribed = terhubung && subscribeButton.dataset.subscribed === "true";
        document.getElementById("btn-unsubscribe").disabled = !subscribed;
        document.getElementById("mqtt-topic").disabled = subscribed;
        document.getElementById("mqtt-qos").disabled = subscribed;
    } catch (error) {
        document.getElementById("btn-subscribe").disabled = true;
        statusKoneksi.textContent = "Status koneksi tidak diketahui";
    }
}


function kunciField(terhubung) {
    const fields = document.querySelectorAll(
        "#mqtt-config-form input, #mqtt-config-form select"
    );

    fields.forEach(function (field) {
        if (terhubung && document.activeElement === field) {
            field.blur();
        }
        field.disabled = terhubung;
    });
}
// Periksa koneksi backend setiap halaman dibuka atau di-refresh.
cekStatusKoneksi();

