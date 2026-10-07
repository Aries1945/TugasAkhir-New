package com.example.TugasAkhir.mqtt;

import com.example.TugasAkhir.service.SuhuService;
import com.example.TugasAkhir.service.PesanErrorMqtt;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;

@Service
public class MqttClientManager {

 
    private volatile MqttClient client; // koneksi ke broker MQTT
    private final SuhuService dataSuhu; // tempat simpan data suhu
    private String topicAktif = "";
    private int qosAktif = 0;
    private MqttCallbackHandler statusMqtt;
    private MqttConnectionOptions options;

    
    // RECORD untuk status subscription (mirip DTO sederhana)

    public record StatusSubscription(boolean connected, boolean subscribed, String topic, int qos,
            boolean reconnecting, String message) {
    }

    public synchronized StatusSubscription statusSubscription() {
        boolean connected = cekKoneksi();
        boolean subscribed = connected && statusMqtt != null && statusMqtt.sudahSubscribe();
        boolean reconnecting = client != null && !connected;
        String pesan = statusMqtt == null ? "Hubungkan broker untuk mulai menerima data." : statusMqtt.ambilPesan();
        if (connected && pesan.equals("Menghubungkan ke broker..."))
            pesan = "Terhubung. Isi topic dan pilih Subscribe.";
        return new StatusSubscription(connected, subscribed, topicAktif, qosAktif, reconnecting, pesan);
    }

    public MqttClientManager(SuhuService dataSuhu) {
        this.dataSuhu = dataSuhu;
    }

    public synchronized void koneksi(String host, int port, String clientId, String username, String password,
            boolean tls) throws MqttException {

        if (client != null) {
            throw new IllegalArgumentException("Client masih aktif. Disconnect terlebih dahulu.");
        }
        topicAktif = "";
        qosAktif = 0;
        String protocol;

        if (tls) {
            protocol = "ssl://";
        } else {
            protocol = "tcp://";
        }

        String brokerUrl = protocol + host + ":" + port;

        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        options = new MqttConnectionOptions();
        // Berikan tempat penyimpanan suhu kepada callback.
        statusMqtt = new MqttCallbackHandler(dataSuhu);
        // Minta Paho memanggil callback ketika pesan atau kejadian koneksi diterima.
        client.setCallback(statusMqtt);

        options.setConnectionTimeout(10);
        options.setCleanStart(true);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);

        // username bersifat opsional
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);

        }
        // ini untuk supaya password bersifat opsional, dan karna paho tidak bisa String
        // jadi di convert passwordnya jadi byte[]

        if (password != null && !password.isEmpty()) {
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        }

        try {
            client.connect(options);
        } catch (MqttException error) {
            try {
                putusKoneksi();
            } catch (MqttException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
        dataSuhu.bersihkan();

    }

    public synchronized void putusKoneksi() throws MqttException {

        if (client == null) { // jika belom ada client maka null
            return;
        }

        // Hentikan client ini, termasuk ketika jaringan sedang terputus.
        options.setAutomaticReconnect(false);
        try {
            if (client.isConnected())
                client.disconnect();
        } finally {
            client.close(true);
            client = null;
            statusMqtt = null;
            topicAktif = "";
            qosAktif = 0;
        }

    }

    public boolean cekKoneksi() {

        MqttClient clientSekarang = client;
        return clientSekarang != null && clientSekarang.isConnected();
    }

    public synchronized void unsubscribe(String topic) throws MqttException {
        if (!cekKoneksi()) {
            throw new IllegalStateException("Hubungkan broker terlebih dahulu.");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic harus diisi.");
        }
        client.unsubscribe(topic);
        if (topic.equals(topicAktif)) {
            topicAktif = "";
            qosAktif = 0;
            statusMqtt.selesaiUnsubscribe();
        }
    }

    public synchronized void subscribe(String topic, int qos) throws MqttException {
        if (!cekKoneksi()) {
            throw new IllegalStateException(
                    "Hubungkan broker terlebih dahulu.");
        }

        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException(
                    "Topic harus diisi.");
        }

        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException(
                    "QoS harus 0, 1, atau 2.");
        }

        if (statusMqtt.sudahSubscribe() && !topicAktif.equals(topic)) {
            throw new IllegalStateException("Unsubscribe topic aktif terlebih dahulu.");
        }

        int nomorKoneksi = statusMqtt.ambilNomorKoneksi();
        // Subscribe tanpa listener per-topic. Pesan diterima oleh
        // MqttCallbackHandler.messageArrived().
        var hasil = client.subscribe(topic, qos);
        int qosDiberikan = hasil.getGrantedQos()[0];
        if (qosDiberikan < 0 || qosDiberikan > 2) {
            throw new IllegalArgumentException("Broker menolak subscription. " + PesanErrorMqtt.dariKode(qosDiberikan));
        }
        topicAktif = topic;
        qosAktif = qosDiberikan;
        statusMqtt.selesaiSubscribe(nomorKoneksi);
    }
}
