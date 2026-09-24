package com.example.TugasAkhir.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.springframework.stereotype.Service;

import com.example.TugasAkhir.websocket.DashboardWebSocketHandler;


@Service
public class subscriberService {

// =====================================================================
// FIELD (variabel milik class)
// =====================================================================
private volatile MqttClient client;          // koneksi ke broker MQTT
private final SuhuService dataSuhu;          // tempat simpan data suhu
private final DashboardWebSocketHandler wsHandler; // pengirim WebSocket
private String topicAktif = "";
private int qosAktif = 0;

// =====================================================================
// RECORD untuk status subscription (mirip DTO sederhana)

public record StatusSubscription(boolean connected, boolean subscribed, String topic, int qos) {}

public synchronized StatusSubscription statusSubscription() {
    boolean connected = cekKoneksi();
    return new StatusSubscription(connected, connected && !topicAktif.isEmpty(),
            connected ? topicAktif : "", qosAktif);
}


public subscriberService(SuhuService dataSuhu, DashboardWebSocketHandler wsHandler) {
    this.dataSuhu = dataSuhu;
    this.wsHandler = wsHandler;
}

// =====================================================================
// KIRIM DATA KE DASHBOARD VIA WEBSOCKET
//
// Cara kerjanya sederhana:
// 1. Ambil semua data suhu dari SuhuService
// 2. Bangun string JSON secara manual (tanpa library tambahan)
// 3. Kirim JSON tersebut ke semua browser yang terhubung via WebSocket
//
// Method ini dipanggil setiap kali:
// - Ada data suhu baru masuk dari MQTT
// - Status koneksi broker berubah (connect / disconnect)
// =====================================================================
private void kirimWebSocket() {
    try {
        // Ambil data
        boolean connected = cekKoneksi();
        long waktuServer = System.currentTimeMillis();
        List<SuhuService.Sensor> daftarSensor = dataSuhu.ambilData();

        // Bangun JSON manual pakai StringBuilder
        String json = buatJson(connected, waktuServer, daftarSensor);

        // Kirim ke semua browser yang sedang buka dashboard
        wsHandler.broadcast(json);

    } catch (Exception e) {
        System.out.println("Gagal kirim WebSocket: " + e.getMessage());
    }
}


private String buatJson(boolean connected, long waktuServer, List<SuhuService.Sensor> daftarSensor) {
    StringBuilder sb = new StringBuilder();

    sb.append("{");
    sb.append("\"connected\":").append(connected);
    sb.append(",\"waktuServer\":").append(waktuServer);
    sb.append(",\"sensor\":[");

    // Loop setiap sensor
    for (int i = 0; i < daftarSensor.size(); i++) {
        if (i > 0) sb.append(","); // koma pemisah antar sensor

        SuhuService.Sensor sensor = daftarSensor.get(i);
        sb.append("{");
        sb.append("\"topic\":\"").append(sensor.topic()).append("\"");
        sb.append(",\"pembacaan\":[");

        // Loop setiap pembacaan suhu dalam sensor ini
        List<SuhuService.Pembacaan> pembacaan = sensor.pembacaan();
        for (int j = 0; j < pembacaan.size(); j++) {
            if (j > 0) sb.append(","); // koma pemisah antar pembacaan

            SuhuService.Pembacaan p = pembacaan.get(j);
            sb.append("{");
            sb.append("\"waktu\":").append(p.waktu());
            sb.append(",\"suhu\":").append(p.suhu());
            sb.append("}");
        }

        sb.append("]}"); // tutup pembacaan & sensor
    }

    sb.append("]}"); // tutup array sensor & object utama
    return sb.toString();
}


public synchronized void koneksi( String host, int port,String clientId,String username, String password,boolean tls) throws MqttException{

    if (cekKoneksi()) {
        throw new IllegalArgumentException("Broker sudah terhubung. Disconnect terlebih dahulu.");
    }
    topicAktif = "";
    qosAktif = 0;
    String protocol;

    if(tls){
        protocol = "ssl://";
    }else{
        protocol = "tcp://";
    }

  String brokerUrl = protocol + host + ":" + port;


  client = new MqttClient(brokerUrl, clientId, new MemoryPersistence() );

  MqttConnectionOptions options = new MqttConnectionOptions();

  options.setConnectionTimeout(10);
  options.setCleanStart(true);
  options.setKeepAliveInterval(60);
  options.setAutomaticReconnect(true);

  //username bersifat opsional
if (username != null && !username.isEmpty()) {
    options.setUserName(username);
    
}
//ini untuk supaya password bersifat opsional, dan karna paho tidak bisa String jadi di convert passwordnya jadi byte[]
   
if (password != null && !password.isEmpty()) {
    options.setPassword(password.getBytes(StandardCharsets.UTF_8));
}

  client.connect(options);
  dataSuhu.bersihkan();

  // Beritahu dashboard bahwa broker sudah terhubung
  kirimWebSocket();

}

// =====================================================================
// DISCONNECT DARI BROKER
// =====================================================================
public synchronized void putusKoneksi() throws MqttException{

    if(client == null){ // jika belom ada client maka null
        return;
    }

    if(client.isConnected()){ // jika client masih terhubung dengan broker maka disconnect
        client.disconnect();
    }
    client.close(); // hapus resource client
    client = null;
    topicAktif = "";

    // Beritahu dashboard bahwa broker sudah terputus
    kirimWebSocket();
}

public boolean cekKoneksi(){
 
    return client != null && client.isConnected();
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
    }
}

// =====================================================================
// SUBSCRIBE KE TOPIC MQTT
//
// Di sinilah data suhu diterima dari broker MQTT.
// Setiap pesan masuk:
//   1. Parse payload jadi angka suhu
//   2. Simpan ke SuhuService (memori)
//   3. Langsung kirim ke dashboard via WebSocket  <-- INI YANG BARU
// =====================================================================
public synchronized void subscribe(String topic, int qos) throws MqttException {
    if (!cekKoneksi()) {
        throw new IllegalStateException(
            "Hubungkan broker terlebih dahulu."
        );
    }

    if (topic == null || topic.isBlank()) {
        throw new IllegalArgumentException(
            "Topic harus diisi."
        );
    }

    if (qos < 0 || qos > 2) {
        throw new IllegalArgumentException(
            "QoS harus 0, 1, atau 2."
        );
    }

    if (!topicAktif.isEmpty() && !topicAktif.equals(topic)) {
        throw new IllegalStateException("Unsubscribe topic aktif terlebih dahulu.");
    }

    // ============================================================
    // CALLBACK: fungsi ini dipanggil setiap kali pesan MQTT masuk
    // ============================================================
    IMqttMessageListener penerimaPesan = (topicMasuk, pesan) -> {
        String isi = new String(pesan.getPayload(), StandardCharsets.UTF_8).trim();
        try {
            double suhu = Double.parseDouble(isi);
            if (!Double.isFinite(suhu)) return;

            // 1. Simpan data suhu ke memori
            dataSuhu.simpan(topicMasuk, suhu);

            // 2. Kirim langsung ke dashboard via WebSocket
            kirimWebSocket();

            System.out.println("Topic: " + topicMasuk + " | Suhu: " + suhu + " °C");
        } catch (NumberFormatException error) {
            System.out.println("Payload bukan angka suhu pada topic: " + topicMasuk);
        }
    };

    // Pakai overload ini karena overload String + listener di Paho 1.2.5
    // memanggil dirinya sendiri berulang kali (StackOverflowError).
    MqttSubscription[] topikPilihan = { new MqttSubscription(topic, qos) };
    IMqttMessageListener[] penerima = { penerimaPesan };
    var hasil = client.subscribe(topikPilihan, penerima);
    int qosDiberikan = hasil.getGrantedQos()[0];
    if (qosDiberikan < 0 || qosDiberikan > 2) {
        throw new IllegalArgumentException("Broker menolak subscription. Periksa izin topic.");
    }
    topicAktif = topic;
    qosAktif = qosDiberikan;
}
}
