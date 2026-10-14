package com.example.TugasAkhir.mqtt;

import com.example.TugasAkhir.service.SensorDataService;
import com.example.TugasAkhir.model.SensorData;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

// Satu objek untuk satu client. Paho memanggil callback ini secara otomatis.
public class MqttCallbackHandler implements MqttCallback {
    // Tempat menyimpan suhu agar bisa dibaca dashboard melalui REST API.
    private final SensorDataService dataSuhu;

    // Gunakan tempat penyimpanan yang sama dengan MqttClientManager.
    public MqttCallbackHandler(SensorDataService dataSuhu) {
        this.dataSuhu = dataSuhu;
    }

    private boolean subscribed = false;
    private int nomorKoneksi = 0;
    private String pesan = "Menghubungkan ke broker...";

    public synchronized boolean sudahSubscribe() {
        return subscribed;
    }

    public synchronized String ambilPesan() {
        return pesan;
    }
    //untuk mencegah hasil subscribe dari koneksi lama supaya tidak dianggap masih berlaku saat koneksi terhapus
    public synchronized int ambilNomorKoneksi() {
        return nomorKoneksi;
    }

    public synchronized void selesaiSubscribe(int nomorSebelumSubscribe) {
        // Jangan menerima hasil subscribe lama jika koneksi sudah sempat putus.
        if (nomorKoneksi == nomorSebelumSubscribe) {
            subscribed = true;
            pesan = "Subscription aktif. Data suhu siap diterima.";
        }
    }

    public synchronized void selesaiUnsubscribe() {
        subscribed = false;
        pesan = "Subscription dihentikan. Anda bisa memilih topic lain.";
    }

    @Override
    public synchronized void disconnected(MqttDisconnectResponse response) {
        nomorKoneksi++;
        subscribed = false;
        // Jaringan putus: gunakan kode dan pesan exception asli Paho.
        if (response.getException() != null) {
            MqttException error = response.getException();
            pesan = "Reason code: " + error.getReasonCode() + ". Detail Paho: " + error.toString();
        } else {
            // Broker mengirim DISCONNECT: tampilkan reason string jika disertakan.
            pesan = "Reason code: " + response.getReturnCode();
            String alasan = response.getReasonString();
            if (alasan != null && !alasan.isBlank()) {
                pesan += ". Reason string: " + alasan;
            }
        }
        pesan += " Paho akan mencoba menghubungkan kembali.";
    }

    @Override
    public synchronized void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            pesan = "Terhubung kembali. Pilih Subscribe untuk menerima data lagi.";
        }
    }

    @Override
    public synchronized void mqttErrorOccurred(MqttException error) {
        // Pesan diambil dari Paho, tanpa tabel terjemahan kode buatan aplikasi.
        pesan = "Reason code: " + error.getReasonCode() + ". Detail Paho: " + error.toString();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 1. Ubah isi pesan dari byte menjadi teks, misalnya "27.5".
        String isi = new String(message.getPayload(), StandardCharsets.UTF_8).trim();

        try {
            // 2. Ubah teks menjadi angka suhu.
            double suhu = Double.parseDouble(isi);

            // 3. Abaikan NaN dan Infinity karena bukan pembacaan suhu yang valid.
            if (!Double.isFinite(suhu)) {
                return;
            }

            // 4. Simpan suhu beserta topic sensor. Dashboard mengambilnya lewat REST API.
            SensorData data = new SensorData(suhu, topic, LocalDateTime.now());
            dataSuhu.processData(data);
            System.out.println("Topic: " + topic + " | Suhu: " + suhu + " °C");
        } catch (NumberFormatException error) {
            // Jika pesan bukan angka, abaikan pesan itu dan tetap terima pesan berikutnya.
            System.out.println("Payload bukan angka suhu pada topic: " + topic);
        }
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        // Khusus publish. Aplikasi ini hanya subscriber.
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        // Tidak diperlukan untuk login username/password biasa.
    }
}
