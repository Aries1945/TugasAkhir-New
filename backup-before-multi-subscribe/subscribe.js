const tombolSubscribe = document.getElementById("btn-subscribe");
const tombolUnsubscribe = document.getElementById("btn-unsubscribe");
let topicAktif = "";

tombolSubscribe.addEventListener("click", SubscribeBroker);
tombolUnsubscribe.addEventListener("click", UnsubscribeBroker);

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
        if (!berhasilSubscribe) {
            tombolSubscribe.textContent = "Subscribe";
            await cekStatusKoneksi();
        }
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
