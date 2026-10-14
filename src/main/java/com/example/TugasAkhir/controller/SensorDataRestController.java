package com.example.TugasAkhir.controller;



import com.example.TugasAkhir.dto.SensorDetailResponse;
import com.example.TugasAkhir.dto.SensorSummaryResponse;
import com.example.TugasAkhir.model.SensorData;
import com.example.TugasAkhir.service.SensorDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensor")
@CrossOrigin(origins = "*")
public class SensorDataRestController {

    @Autowired
    // Gunakan service yang sudah menangani data suhu dalam proyek ini.
    private SensorDataService sensorDataService;

    // Endpoint untuk daftar kartu sensor di Dashboard & History
    @GetMapping("/sensors")
    public ResponseEntity<List<SensorSummaryResponse>> getConnectedSensors() {
        return ResponseEntity.ok(sensorDataService.getSensorSummaries());
    }

    // Endpoint untuk melihat data detail & grafik suatu sensor di History
    @GetMapping("/sensors/detail")
    public ResponseEntity<SensorDetailResponse> getSensorDetail(@RequestParam("topic") String topic) {
        List<SensorData> recentData = sensorDataService.getRecentDataBySensor(topic);
        long totalMessages = sensorDataService.countDataBySensor(topic);

        SensorDetailResponse response = new SensorDetailResponse(topic, totalMessages, recentData);
        return ResponseEntity.ok(response);
    }
}
