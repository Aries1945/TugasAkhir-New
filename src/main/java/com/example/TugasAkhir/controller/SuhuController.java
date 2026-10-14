package com.example.TugasAkhir.controller;

import com.example.TugasAkhir.service.SensorDataService;
import com.example.TugasAkhir.mqtt.MqttClientManager;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class SuhuController {
    private final SensorDataService dataSuhu;
    // Status subscription berasal dari manager aplikasi, bukan client Paho langsung.
    private final MqttClientManager mqtt;

    public SuhuController(SensorDataService dataSuhu, MqttClientManager mqtt) {
        this.dataSuhu = dataSuhu;
        this.mqtt = mqtt;
    }

    public record DataDashboard(boolean connected, long waktuServer, List<SensorDataService.DataSensor> sensor,
            boolean subscribed, boolean reconnecting, String message) {}

    @GetMapping("/api/suhu")
    public DataDashboard suhu() {
        var status = mqtt.statusSubscription();
        return new DataDashboard(status.connected(), System.currentTimeMillis(), dataSuhu.ambilData(),
                status.subscribed(), status.reconnecting(), status.message());
    }

    // Halaman History membaca database, termasuk catatan sebelum aplikasi dimulai ulang.
    @GetMapping("/api/riwayat")
    public DataDashboard riwayat() {
        var status = mqtt.statusSubscription();
        return new DataDashboard(status.connected(), System.currentTimeMillis(), dataSuhu.ambilRiwayat(),
                status.subscribed(), status.reconnecting(), status.message());
    }
}
