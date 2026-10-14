package com.example.TugasAkhir.dto;

import com.example.TugasAkhir.model.SensorData;
import java.util.List;

// Jawaban untuk detail sensor: topic, jumlah pesan, dan catatan terbaru.
public record SensorDetailResponse(String topic, long totalMessages, List<SensorData> recentData) {}
