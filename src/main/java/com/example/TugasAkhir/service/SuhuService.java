package com.example.TugasAkhir.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SuhuService {
    // Waktu menggunakan milidetik agar bisa dibaca langsung oleh JavaScript.
    public record Pembacaan(long waktu, double suhu) {}
    public record Sensor(String topic, List<Pembacaan> pembacaan) {}

    private final Map<String, List<Pembacaan>> daftarSensor = new LinkedHashMap<>();

    public synchronized void simpan(String topic, double suhu) {
        if (!Double.isFinite(suhu)) return;

        if (!daftarSensor.containsKey(topic)) {
            // Batasi pemakaian memori jika menerima banyak topic.
            if (daftarSensor.size() >= 32) {
                daftarSensor.remove(daftarSensor.keySet().iterator().next());
            }
            daftarSensor.put(topic, new ArrayList<>());
        }

        List<Pembacaan> riwayat = daftarSensor.get(topic);
        riwayat.add(new Pembacaan(System.currentTimeMillis(), suhu));
        // Maksimal 600 pesan terakhir per sensor, disimpan selama aplikasi hidup.
        while (riwayat.size() > 600) riwayat.remove(0);
    }

    public synchronized List<Sensor> ambilData() {
        List<Sensor> hasil = new ArrayList<>();
        for (var sensor : daftarSensor.entrySet()) {
            hasil.add(new Sensor(sensor.getKey(), List.copyOf(sensor.getValue())));
        }
        return hasil;
    }

    public synchronized void bersihkan() {
        daftarSensor.clear();
    }
}
