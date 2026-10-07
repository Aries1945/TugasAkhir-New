package com.example.TugasAkhir.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.TugasAkhir.repository.RiwayatSuhuRepository;

@Service
public class SuhuService {
    private final RiwayatSuhuRepository database;

    // Spring memberikan repository agar service bisa menyimpan ke PostgreSQL.
    public SuhuService(RiwayatSuhuRepository database) {
        this.database = database;
    }
    // Satu catatan berisi waktu diterima (milidetik) dan nilai suhu.
    public record Pembacaan(long waktu, double suhu) {}
    // Satu sensor berisi nama topic dan daftar catatan suhunya.
    public record Sensor(String topic, List<Pembacaan> pembacaan) {}

    // Map seperti buku: topic adalah nama halaman, List adalah catatan di halaman itu.
    private final Map<String, List<Pembacaan>> daftarSensor = new LinkedHashMap<>();

    // synchronized membuat penulisan dan pembacaan data berjalan bergantian.
    public synchronized void simpan(String topic, double suhu) {
        // Abaikan nilai yang bukan angka suhu biasa, seperti NaN dan Infinity.
        if (!Double.isFinite(suhu)) {
            return;
        }

        // Jika sensor belum ada, siapkan daftar kosong untuk sensor tersebut.
        if (!daftarSensor.containsKey(topic)) {
            daftarSensor.put(topic, new ArrayList<>());
        }

        // Buat catatan baru, lalu tambahkan tanpa menghapus catatan sebelumnya.
        long waktuSekarang = System.currentTimeMillis();
        // Simpan ke database dahulu. Jika gagal, jangan menganggap catatan tersimpan.
        database.simpan(topic, suhu, waktuSekarang);
        Pembacaan catatanBaru = new Pembacaan(waktuSekarang, suhu);
        List<Pembacaan> riwayat = daftarSensor.get(topic);
        riwayat.add(catatanBaru);
    }

    // Buat salinan semua data untuk dikirim melalui REST API.
    public synchronized List<Sensor> ambilData() {
        List<Sensor> hasil = new ArrayList<>();

        // Baca sensor satu per satu. Salinan menjaga daftar asli tetap terpisah.
        for (String topic : daftarSensor.keySet()) {
            List<Pembacaan> riwayatAsli = daftarSensor.get(topic);
            List<Pembacaan> salinanRiwayat = new ArrayList<>(riwayatAsli);
            Sensor sensor = new Sensor(topic, salinanRiwayat);
            hasil.add(sensor);
        }
        return hasil;
    }

    // Baca riwayat database lalu kelompokkan berdasarkan topic untuk halaman History.
    public List<Sensor> ambilRiwayat() {
        Map<String, List<Pembacaan>> kelompok = new LinkedHashMap<>();
        for (RiwayatSuhuRepository.Catatan catatan : database.ambilSemua()) {
            String topic = catatan.topic();
            if (!kelompok.containsKey(topic)) {
                kelompok.put(topic, new ArrayList<>());
            }
            kelompok.get(topic).add(new Pembacaan(catatan.waktu(), catatan.suhu()));
        }

        List<Sensor> hasil = new ArrayList<>();
        for (String topic : kelompok.keySet()) {
            hasil.add(new Sensor(topic, kelompok.get(topic)));
        }
        return hasil;
    }

    // Hanya bersihkan dashboard. Riwayat dalam database tidak dihapus.
    public synchronized void bersihkan() {
        daftarSensor.clear();
    }
}
