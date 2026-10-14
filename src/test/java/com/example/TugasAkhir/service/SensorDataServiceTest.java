package com.example.TugasAkhir.service;

import com.example.TugasAkhir.model.Sensor;
import com.example.TugasAkhir.model.SensorData;
import com.example.TugasAkhir.repository.SensorDataRepository;
import com.example.TugasAkhir.repository.SensorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Gunakan database tiruan agar pengujian tidak menambah data ke database pengguna.
@ExtendWith(MockitoExtension.class)
class SensorDataServiceTest {
    @Mock SensorDataRepository dataRepository;
    @Mock SensorRepository sensorRepository;
    @InjectMocks SensorDataService service;

    // Callback baru harus menyimpan data dan menyediakan bentuk data REST yang lama.
    @Test
    void pesanTersimpanBisaDibacaDashboard() {
        Sensor sensor = new Sensor("sensor/suhu/affe");
        when(sensorRepository.findByNamaSensor(sensor.getNamaSensor())).thenReturn(Optional.of(sensor));
        when(dataRepository.findMaxUrutan()).thenReturn(10);
        service.init();
        SensorData data = new SensorData(28.5, sensor.getNamaSensor(), LocalDateTime.now());

        service.processData(data);

        verify(dataRepository).save(data);
        assertEquals(11, data.getUrutan());
        assertSame(sensor, data.getSensor());
        assertEquals(28.5, service.ambilData().get(0).pembacaan().get(0).suhu());
        // Mengubah hasil API tidak boleh menghapus data asli.
        service.ambilData().get(0).pembacaan().clear();
        assertEquals(1, service.ambilData().get(0).pembacaan().size());
    }

    // Error database tidak boleh tertelan dan dianggap sebagai penyimpanan berhasil.
    @Test
    void gagalSimpanTidakDitampilkanSebagaiDataTersimpan() {
        Sensor sensor = new Sensor("affe");
        when(sensorRepository.findByNamaSensor("affe")).thenReturn(Optional.of(sensor));
        SensorData data = new SensorData(28.5, "affe", LocalDateTime.now());
        when(dataRepository.save(data)).thenThrow(new DataIntegrityViolationException("uji gagal"));

        assertThrows(DataIntegrityViolationException.class, () -> service.processData(data));
        assertTrue(service.ambilData().isEmpty());
    }

    // Pergantian service tidak boleh membuat History kembali menampilkan sensor ganda.
    @Test
    void historyTetapMenggabungkanNamaSensor() {
        var nama = List.of("affe", "sensor/suhu/affe");
        when(dataRepository.findDistinctSensorTopics()).thenReturn(nama);
        SensorData terbaru = new SensorData(30.0, "sensor/suhu/affe", LocalDateTime.now());
        when(dataRepository.findFirstBySensor_NamaSensorInOrderByWaktuDescIdDesc(nama)).thenReturn(terbaru);
        when(dataRepository.countBySensor_NamaSensorIn(nama)).thenReturn(7L);

        var ringkasan = service.getSensorSummaries();

        assertEquals(1, ringkasan.size());
        assertEquals("affe", ringkasan.get(0).topic());
        assertEquals(7, ringkasan.get(0).totalMessages());
        assertEquals(30.0, ringkasan.get(0).suhu());
    }
}
