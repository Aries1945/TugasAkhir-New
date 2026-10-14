package com.example.TugasAkhir.repository;

import com.example.TugasAkhir.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {
    // Ambil 50 data suhu terakhir berdasarkan nama sensor untuk grafik.
    List<SensorData> findTop50BySensor_NamaSensorOrderByWaktuDesc(String namaSensor);
    // Ambil 1 data suhu paling akhir untuk ringkasan kartu sensor.
    SensorData findFirstBySensor_NamaSensorOrderByWaktuDesc(String namaSensor);
    // Hitung total jumlah data suhu yang masuk untuk sensor tertentu.
    long countBySensor_NamaSensor(String namaSensor);
    // Gabungkan nama lama (affe) dan topic lengkap (sensor/suhu/affe) saat membaca History.
    List<SensorData> findTop50BySensor_NamaSensorInOrderByWaktuDescIdDesc(List<String> namaSensor);
    // Baca nilai terbaru dari kedua bentuk nama tersebut.
    SensorData findFirstBySensor_NamaSensorInOrderByWaktuDescIdDesc(List<String> namaSensor);
    // Hitung semua catatan, termasuk yang tersimpan menggunakan nama lama.
    long countBySensor_NamaSensorIn(List<String> namaSensor);
    // Ambil daftar nama topik sensor yang unik.
    @Query("SELECT DISTINCT s.sensor.namaSensor FROM SensorData s WHERE s.sensor.namaSensor IS NOT NULL ORDER BY s.sensor.namaSensor ASC")
    List<String> findDistinctSensorTopics();
    // Cari nilai urutan tertinggi terakhir di database.
    @Query("SELECT COALESCE(MAX(s.urutan), 0) FROM SensorData s")
    int findMaxUrutan();
}
