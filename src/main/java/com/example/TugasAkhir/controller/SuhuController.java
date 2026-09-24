package com.example.TugasAkhir.controller;

import com.example.TugasAkhir.service.SuhuService;
import com.example.TugasAkhir.service.subscriberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class SuhuController {
    private final SuhuService dataSuhu;
    private final subscriberService mqtt;

    public SuhuController(SuhuService dataSuhu, subscriberService mqtt) {
        this.dataSuhu = dataSuhu;
        this.mqtt = mqtt;
    }

    public record DataDashboard(boolean connected, long waktuServer, List<SuhuService.Sensor> sensor) {}

    @GetMapping("/api/suhu")
    public DataDashboard suhu() {
        return new DataDashboard(mqtt.cekKoneksi(), System.currentTimeMillis(), dataSuhu.ambilData());
    }
}
