package com.example.TugasAkhir.mqtt;

import com.example.TugasAkhir.service.SuhuService;
import com.example.TugasAkhir.service.PesanErrorMqtt;

import java.nio.charset.StandardCharsets;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

// Satu objek untuk satu client. Paho memanggil callback ini secara otomatis.
public class MqttCallbackHandler implements MqttCallback {
    // Tempat menyimpan suhu agar bisa dibaca dashboard melalui REST API.
    private final SuhuService dataSuhu;

    // Gunakan tempat penyimpanan yang sama dengan MqttClientManager.
    public MqttCallbackHandler(SuhuService dataSuhu) {
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
        if (response.getException() != null) {
            pesan = PesanErrorMqtt.dariException(response.getException());
        } else {
            pesan = PesanErrorMqtt.dariKode(response.getReturnCode());
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
        pesan = PesanErrorMqtt.dariException(error);
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
            dataSuhu.simpan(topic, suhu);
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
