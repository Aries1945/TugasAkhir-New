package com.example.TugasAkhir.mqtt;

import java.io.File;
import java.util.Scanner;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class PublishRuangKelas {
    public static void main(String[] args) {
        String broker = "tcp://mqtt.eclipseprojects.io:1883";
        String clientId = "publisher-ruang-kelas-" + System.currentTimeMillis();
        File file = new File("data-dummy/suhu-ruang-kelas.txt");
        if (!file.exists()) { System.out.println("File tidak ditemukan!"); return; }
        try {
            MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectionOptions opsi = new MqttConnectionOptions();
            opsi.setAutomaticReconnect(true); opsi.setCleanStart(true); client.connect(opsi);
            Scanner fileScanner = new Scanner(file);
            while (fileScanner.hasNextLine()) {
                String baris = fileScanner.nextLine();
                if (baris.startsWith("SENSE")) {
                    String[] kata = baris.split("\\s+");
                    String namaSensor = kata[2]; String topik = "sensor/suhu/" + namaSensor;
                    String suhu = kata[5].replace("TEMP:", "").replace("°C", "").trim();
                    MqttMessage pesan = new MqttMessage(suhu.getBytes()); pesan.setQos(1);
                    while (!client.isConnected()) { System.out.println("Koneksi terputus, menunggu sambung ulang..."); Thread.sleep(1000); }
                    client.publish(topik, pesan); System.out.println("Data Terkirim -> Topik: " + topik + " | Suhu: " + suhu + " °C"); Thread.sleep(500);
                }
            }
            fileScanner.close(); client.disconnect(); client.close(); System.out.println("Pengiriman data selesai.");
        } catch (Exception e) { System.out.println("Terjadi error: " + e.getMessage()); }
    }
}
