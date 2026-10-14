const tombolSubscribe = document.getElementById("btn-subscribe");
let topicAktif = "";

tombolSubscribe.addEventListener("click", () => {
    if (sedangMemproses || tombolSubscribe.disabled) return;
    return tombolSubscribe.dataset.subscribed === "true" ? UnsubscribeBroker() : SubscribeBroker();
});

// Satu respons backend menentukan aksi kedua tombol, termasuk setelah refresh.
async function cekStatusSubscription() {
    try {
        const jawaban = await fetch("/api/mqtt/subscription-status", { cache: "no-store" });
        if (!jawaban.ok) throw new Error("Gagal membaca status");
        const status = await jawaban.json();
        if (typeof status.connected !== "boolean" || typeof status.subscribed !== "boolean") {
            throw new Error("Status tidak valid");
        }
        const subscribed = status.connected && status.subscribed;
        topicAktif = status.topic;
        tampilkanStatusKoneksi(status.connected || status.reconnecting);
        if (status.reconnecting) statusKoneksi.textContent = "Menghubungkan kembali...";
        tombolSubscribe.dataset.subscribed = String(subscribed);
        tombolSubscribe.textContent = subscribed ? "Unsubscribe"
            : status.connected && status.topic ? "Subscribe ulang" : "Subscribe";
        tombolSubscribe.classList.toggle("btn-primary", !subscribed);
        tombolSubscribe.classList.toggle("btn-danger-outline", subscribed);
        tombolSubscribe.disabled = !status.connected;
        document.getElementById("mqtt-topic").disabled = !status.connected || subscribed;
        document.getElementById("mqtt-qos").disabled = !status.connected || subscribed;
        if (subscribed || (status.topic && !document.getElementById("mqtt-topic").value)) {
            document.getElementById("mqtt-topic").value = status.topic;
            document.getElementById("mqtt-qos").value = status.qos;
        }
        document.getElementById("subscription-help").textContent = subscribed
            ? status.message + " QoS yang diberikan broker: " + status.qos + ". Pilih Unsubscribe untuk berhenti."
            : status.message;
    } catch (error) {
        tombolConnect.textContent = "Periksa status";
        tombolConnect.disabled = true;
        tombolSubscribe.textContent = "Subscribe";
        tombolSubscribe.disabled = true;
        document.getElementById("mqtt-topic").disabled = true;
        document.getElementById("mqtt-qos").disabled = true;
        kunciField(true);
        statusKoneksi.textContent = "Status koneksi tidak diketahui";
        document.getElementById("subscription-help").textContent = "Status belum dapat dibaca. Refresh untuk mencoba lagi.";
    }
}

// Periksa status melalui REST API. Lewati pemeriksaan saat pengguna sedang mengirim perintah.
setInterval(async () => {
    if (sedangMemproses || document.hidden) return;
    sedangMemproses = true;
    try {
        await cekStatusSubscription();
    } finally {
        sedangMemproses = false;
    }
}, 3000);

async function SubscribeBroker() {
    const topic = document.getElementById("mqtt-topic").value;
    const qos = document.getElementById("mqtt-qos").value;
    if (!topic.trim()) {
        alert("Topic harus diisi.");
        return;
    }
    await ubahSubscription("subscribe", new URLSearchParams({ topic, qos }));
}

async function UnsubscribeBroker() {
    if (!topicAktif) return;
    await ubahSubscription("unsubscribe", new URLSearchParams({ topic: topicAktif }));
}

async function ubahSubscription(aksi, data) {
    if (sedangMemproses) return;
    sedangMemproses = true;
    tombolConnect.disabled = true;
    tombolSubscribe.disabled = true;
    tombolSubscribe.textContent = aksi === "subscribe" ? "Subscribing..." : "Unsubscribing...";
    document.getElementById("mqtt-topic").disabled = true;
    document.getElementById("mqtt-qos").disabled = true;
    try {
        const jawaban = await fetch("/api/mqtt/" + aksi, { method: "POST", body: data });
        alert(await bacaPesan(jawaban));
    } catch (error) {
        alert("Tidak dapat menghubungi server aplikasi.");
    } finally {
        await cekStatusSubscription();
        sedangMemproses = false;
    }
}
