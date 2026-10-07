package com.example.TugasAkhir.repository;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// Semua query memakai tabel sensor dan data_suhu dari skema database aplikasi.
@Repository
public class RiwayatSuhuRepository {
    private final JdbcTemplate database;

    // Spring menyediakan koneksi dari konfigurasi application.properties.
    public RiwayatSuhuRepository(JdbcTemplate database) {
        this.database = database;
    }

    // Bentuk hasil SELECT, bukan entity JPA dan tidak membuat tabel baru.
    public record Catatan(String topic, double suhu, long waktu) {}

    // Jika salah satu query gagal, batalkan seluruh penyimpanan pesan ini.
    @Transactional
    public void simpan(String topic, double suhu, long waktu) {
        // Cari sensor menggunakan nama topic lengkap.
        List<Integer> sensor = database.queryForList(
            "SELECT id_sensor FROM sensor WHERE nama_sensor = ? ORDER BY id_sensor",
            Integer.class, topic);

        int idSensor;
        if (sensor.isEmpty()) {
            // Sensor baru dibuat saat topic tersebut pertama kali mengirim suhu.
            idSensor = database.queryForObject(
                "INSERT INTO sensor (nama_sensor) VALUES (?) RETURNING id_sensor",
                Integer.class, topic);
        } else {
            idSensor = sensor.get(0);
        }

        // Lanjutkan nomor urut dari catatan terakhir sensor ini.
        int urutan = database.queryForObject(
            "SELECT COALESCE(MAX(urutan), 0) + 1 FROM data_suhu WHERE id_sensor = ?",
            Integer.class, idSensor);

        // Parameter ? diisi terpisah agar data tidak dianggap sebagai perintah SQL.
        database.update(
            "INSERT INTO data_suhu (id_sensor, urutan, waktu, suhu) VALUES (?, ?, ?, ?)",
            idSensor, urutan, new Timestamp(waktu), suhu);
    }

    // Gabungkan catatan suhu dengan nama sensor untuk halaman History.
    public List<Catatan> ambilSemua() {
        return database.query(
            "SELECT s.nama_sensor, d.suhu, d.waktu FROM data_suhu d " +
            "JOIN sensor s ON s.id_sensor = d.id_sensor ORDER BY d.waktu, d.id_data",
            (baris, nomor) -> new Catatan(
                baris.getString("nama_sensor"),
                baris.getDouble("suhu"),
                baris.getTimestamp("waktu").getTime()));
    }
}
