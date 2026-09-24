const tombolSubscribe = document.getElementById("btn-subscribe");
const tombolUnsubscribe = document.getElementById("btn-unsubscribe");
let topicAktif = "";

tombolSubscribe.addEventListener("click", SubscribeBroker);
tombolUnsubscribe.addEventListener("click", UnsubscribeBroker);

// Topic aktif disimpan di Java, sehingga tidak hilang saat pindah halaman.
async function cekStatusSubscription() {
    tombolSubscribe.disabled = true;
    tombolUnsubscribe.disabled = true;
    document.getElementById("mqtt-topic").disabled = true;
    document.getElementById("mqtt-qos").disabled = true;
    try {
        const jawaban = await fetch("/api/mqtt/subscription-status", { cache: "no-store" });
        if (!jawaban.ok) throw new Error("Gagal membaca subscription");
        const status = await jawaban.json();
        topicAktif = status.topic;
        tombolSubscribe.textContent = status.subscribed ? "Subscribed" : "Subscribe";
        tombolSubscribe.disabled = !status.connected || status.subscribed;
        tombolUnsubscribe.disabled = !status.connected || !status.subscribed;
        document.getElementById("mqtt-topic").disabled = !status.connected || status.subscribed;
        document.getElementById("mqtt-qos").disabled = !status.connected || status.subscribed;
        if (status.subscribed) {
            document.getElementById("mqtt-topic").value = status.topic;
            document.getElementById("mqtt-qos").value = status.qos;
        }
        document.getElementById("subscription-help").textContent = status.subscribed
            ? "Subscription aktif. Data tetap diterima saat membuka Dashboard."
            : "Hubungkan broker, lalu isi topic dan pilih Subscribe.";
    } catch (error) {
        document.getElementById("subscription-help").textContent = "Status subscription belum dapat dibaca. Refresh untuk mencoba lagi.";
    }
}

async function SubscribeBroker() {
    if (tombolSubscribe.disabled) {
        return;
    }
    const topic = document.getElementById("mqtt-topic").value;
    const qos = document.getElementById("mqtt-qos").value;

    if (topic.trim() === "") {
        alert("Topic harus diisi.");
        return;
    }

    const data = new URLSearchParams();
    data.append("topic", topic);
    data.append("qos", qos);

    tombolSubscribe.disabled = true;
    tombolSubscribe.textContent = "Subscribing...";
    let berhasilSubscribe = false;

    try {
        const jawaban = await fetch("/api/mqtt/subscribe", {
            method: "POST",
            body: data
        });

        const pesan = await bacaPesan(jawaban);
        if (jawaban.ok) {
            berhasilSubscribe = true;
            tombolSubscribe.dataset.subscribed = "true";
            tombolSubscribe.textContent = "Subscribed";
            tombolSubscribe.disabled = true;
            topicAktif = topic;
            tombolUnsubscribe.disabled = false;
            document.getElementById("mqtt-topic").disabled = true;
            document.getElementById("mqtt-qos").disabled = true;
        }
        alert(pesan);
    } catch (error) {
        alert("Tidak dapat menghubungi server aplikasi.");
    } finally {
        await cekStatusSubscription();
    }
}

async function UnsubscribeBroker() {
    if (tombolUnsubscribe.disabled || !topicAktif) return;

    tombolUnsubscribe.disabled = true;
    tombolUnsubscribe.textContent = "Unsubscribing...";
    try {
        const data = new URLSearchParams({ topic: topicAktif });
        const jawaban = await fetch("/api/mqtt/unsubscribe", {
            method: "POST",
            body: data
        });
        const pesan = await bacaPesan(jawaban);
        if (jawaban.ok) {
            topicAktif = "";
            tombolSubscribe.dataset.subscribed = "false";
            tombolSubscribe.textContent = "Subscribe";
            document.getElementById("mqtt-topic").disabled = false;
            document.getElementById("mqtt-qos").disabled = false;
        }
        alert(pesan);
    } catch (error) {
        alert("Tidak dapat menghubungi server aplikasi.");
    } finally {
        tombolUnsubscribe.textContent = "Unsubscribe";
        await cekStatusKoneksi();
    }
}
