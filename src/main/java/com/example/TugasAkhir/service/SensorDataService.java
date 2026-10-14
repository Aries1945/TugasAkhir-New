package com.example.TugasAkhir.service;

import com.example.TugasAkhir.dto.SensorSummaryResponse;
import com.example.TugasAkhir.model.Sensor;
import com.example.TugasAkhir.model.SensorData;
import com.example.TugasAkhir.repository.SensorDataRepository;
import com.example.TugasAkhir.repository.SensorRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SensorDataService {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private SensorRepository sensorRepository;

    // Bentuk data REST API yang sudah digunakan JavaScript dashboard.
    public record Pembacaan(long waktu, double suhu) {}
    public record DataSensor(String topic, List<Pembacaan> pembacaan) {}

    // Simpan pembacaan sesi berjalan agar dashboard tidak terus membaca database.
    private final Map<String, List<Pembacaan>> daftarSensor = new LinkedHashMap<>();

    // Counter untuk auto-increment nomor urut data
    private final AtomicInteger urutanCounter = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // Ambil urutan terakhir dari database agar nomor urut berlanjut
        int maxUrutan = sensorDataRepository.findMaxUrutan();
        urutanCounter.set(maxUrutan);
    }

    /**
     * Memproses data suhu yang diterima dari MQTT:
     * 1. Beri nomor urut otomatis
     * 2. Simpan master sensor jika belum ada
     * 3. Simpan data suhu ke database PostgreSQL
     * 4. Siapkan data agar browser dapat membacanya melalui REST API
     */
    public synchronized void processData(SensorData data) {
        // Abaikan suhu yang tidak valid; jangan simpan NaN atau Infinity.
        if (data == null || data.getSuhu() == null || !Double.isFinite(data.getSuhu())) {
            return;
        }
        // Setiap catatan harus memiliki identitas sensor.
        if (data.getSensorTopic() == null || data.getSensorTopic().isBlank()) {
            throw new IllegalArgumentException("Nama sensor harus diisi.");
        }
        // 1. Beri nomor urut dan waktu sekarang
        data.setUrutan(urutanCounter.incrementAndGet());
        if (data.getWaktu() == null) {
            data.setWaktu(LocalDateTime.now());
        }

        // 2. Simpan master sensor jika belum terdaftar
        Sensor sensor = data.getSensor();
        if (sensor != null) {
            String nama = sensor.getNamaSensor();
            Sensor master = sensorRepository.findByNamaSensor(nama)
                    .orElseGet(() -> sensorRepository.save(new Sensor(nama)));
            data.setSensor(master);
        }

        // 3. Simpan data suhu ke tabel database
        sensorDataRepository.save(data);

        // 4. Tambahkan ke memori hanya setelah database berhasil menyimpan.
        // Jika save gagal, biarkan error diteruskan; jangan menganggap pesan sudah tersimpan.
        String topic = data.getSensorTopic();
        long waktu = data.getWaktu().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (!daftarSensor.containsKey(topic)) {
            daftarSensor.put(topic, new ArrayList<>());
        }
        daftarSensor.get(topic).add(new Pembacaan(waktu, data.getSuhu()));
    }

    // Ambil ringkasan sensor (suhu terakhir, waktu terakhir, total pesan)
    public List<SensorSummaryResponse> getSensorSummaries() {
        // Satu kartu untuk nama pendek dan topic lengkap sensor yang sama.
        Set<String> topics = new LinkedHashSet<>();
        for (String topic : sensorDataRepository.findDistinctSensorTopics()) {
            topics.add(ambilNamaSensor(topic));
        }
        List<SensorSummaryResponse> summaries = new ArrayList<>();

        for (String topic : topics) {
            SensorData latest = sensorDataRepository.findFirstBySensor_NamaSensorInOrderByWaktuDescIdDesc(namaDalamDatabase(topic));
            if (latest != null) {
                long total = countDataBySensor(topic);
                // Urutan field mengikuti DTO proyek: topic, jumlah, suhu, waktu.
                summaries.add(new SensorSummaryResponse(topic, total, latest.getSuhu(), latest.getWaktu()));
            }
        }
        return summaries;
    }

    // Ambil 50 data riwayat terakhir untuk grafik detail sensor
    public List<SensorData> getRecentDataBySensor(String sensorTopic) {
        return sensorDataRepository.findTop50BySensor_NamaSensorInOrderByWaktuDescIdDesc(namaDalamDatabase(sensorTopic));
    }

    // Hitung total data yang masuk untuk sensor tertentu
    public long countDataBySensor(String sensorTopic) {
        return sensorDataRepository.countBySensor_NamaSensorIn(namaDalamDatabase(sensorTopic));
    }

    // REST /api/suhu membaca salinan data sesi berjalan.
    public synchronized List<DataSensor> ambilData() {
        List<DataSensor> hasil = new ArrayList<>();
        for (String topic : daftarSensor.keySet()) {
            hasil.add(new DataSensor(topic, new ArrayList<>(daftarSensor.get(topic))));
        }
        return hasil;
    }

    // REST /api/riwayat tetap menyediakan seluruh riwayat database berdasarkan nama sensor.
    public List<DataSensor> ambilRiwayat() {
        Map<String, List<Pembacaan>> kelompok = new LinkedHashMap<>();
        for (SensorData data : sensorDataRepository.findAll(Sort.by("waktu", "id"))) {
            String nama = ambilNamaSensor(data.getSensorTopic());
            if (!kelompok.containsKey(nama)) {
                kelompok.put(nama, new ArrayList<>());
            }
            long waktu = data.getWaktu().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            kelompok.get(nama).add(new Pembacaan(waktu, data.getSuhu()));
        }
        List<DataSensor> hasil = new ArrayList<>();
        for (String nama : kelompok.keySet()) {
            hasil.add(new DataSensor(nama, kelompok.get(nama)));
        }
        return hasil;
    }

    // Koneksi baru membersihkan memori dashboard saja, bukan database.
    public synchronized void bersihkan() {
        daftarSensor.clear();
    }

    // Contoh: sensor/suhu/affe menjadi affe. Awalan topic lain tetap dipertahankan.
    private String ambilNamaSensor(String topic) {
        String awalan = "sensor/suhu/";
        return topic.startsWith(awalan) ? topic.substring(awalan.length()) : topic;
    }

    // Baca riwayat lama dan baru dalam satu kelompok tanpa mengubah database.
    private List<String> namaDalamDatabase(String topic) {
        String nama = ambilNamaSensor(topic);
        return List.of(nama, "sensor/suhu/" + nama);
    }
}
