package com.example.TugasAkhir.dto;

import java.time.LocalDateTime;

// Jawaban untuk kartu sensor. Waktu menunjukkan kapan data terakhir diterima.
public record SensorSummaryResponse(String topic, long totalMessages, Double suhu, LocalDateTime waktu) {}
