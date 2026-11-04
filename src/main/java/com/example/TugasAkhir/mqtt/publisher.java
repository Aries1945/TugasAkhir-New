package com.example.TugasAkhir.mqtt;




import java.io.File;
import java.util.Scanner;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class publisher {

    public static void main(String[] args) {
        String broker = "tcp://broker.emqx.io:1883";
        // Client ID harus unik: broker memutus koneksi lama jika ada ID yang sama.
        String clientId = "publisher-sensor-" + System.currentTimeMillis();

        // Lokasi file data sensor
        File file = new File("sensing_1779624099815.txt");
        if (!file.exists()) {
            file = new File("../sensing_1779624099815.txt");
        }

        if (!file.exists()) {
            System.out.println("File tidak ditemukan!");
            return;
        }

        try {
            // 1. Buat koneksi ke broker MQTT
            MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectionOptions opsi = new MqttConnectionOptions();
            // Sambungkan ulang otomatis jika koneksi ke broker sempat terputus.
            opsi.setAutomaticReconnect(true);
            opsi.setCleanStart(true);
            client.connect(opsi);
            System.out.println("Berhasil terhubung ke broker MQTT!");

            // 2. Baca file teks baris demi baris menggunakan Scanner
            Scanner fileScanner = new Scanner(file);
            while (fileScanner.hasNextLine()) {
                String baris = fileScanner.nextLine();

                // Hanya proses baris data yang diawali "SENSE"
                if (baris.startsWith("SENSE")) {
                    String[] kata = baris.split("\\s+");

                    // Ambil nama sensor di kolom ke-3 (contoh: beba, affe, bafe)
                    String namaSensor = kata[2];
                    String topik = "sensor/suhu/" + namaSensor;

                    // Ambil suhu di kolom ke-6, hilangkan teks "TEMP:" dan "°C"
                    String suhu = kata[5].replace("TEMP:", "").replace("°C", "").trim();

                    // 3. Kirim data langsung sebagai byte array ke broker MQTT
                    MqttMessage pesan = new MqttMessage(suhu.getBytes());
                    pesan.setQos(1);
                    // Tunggu sampai tersambung kembali sebelum mengirim.
                    while (!client.isConnected()) {
                        System.out.println("Koneksi terputus, menunggu sambung ulang...");
                        Thread.sleep(1000);
                    }
                    client.publish(topik, pesan);

                    System.out.println("Data Terkirim -> Topik: " + topik + " | Suhu: " + suhu + " °C");

                    // Tunggu 2 detik sebelum mengirim baris data berikutnya.
                    Thread.sleep(2000);
                }
            }

            // 4. Tutup scanner dan koneksi MQTT setelah selesai
            fileScanner.close();
            client.disconnect();
            client.close();
            System.out.println("Pengiriman data selesai.");

        } catch (Exception e) {
            System.out.println("Terjadi error: " + e.getMessage());
        }
    }
}
