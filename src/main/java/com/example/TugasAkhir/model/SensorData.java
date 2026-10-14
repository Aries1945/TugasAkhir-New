package com.example.TugasAkhir.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "data_suhu")
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_data")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_sensor", nullable = false)
    private Sensor sensor;

    @Column(name = "urutan", nullable = false)
    private Integer urutan;

    @Column(name = "waktu", nullable = false)
    private LocalDateTime waktu;

    @Column(name = "suhu", nullable = false)
    private Double suhu;

    public SensorData() {}

    public SensorData(Double suhu, String sensorTopic, LocalDateTime waktu) {
        this.suhu = suhu;
        this.sensor = new Sensor(sensorTopic);
        this.waktu = waktu;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Sensor getSensor() { return sensor; }
    public void setSensor(Sensor sensor) { this.sensor = sensor; }

    public Integer getUrutan() { return urutan; }
    public void setUrutan(Integer urutan) { this.urutan = urutan; }

    public LocalDateTime getWaktu() { return waktu; }
    public void setWaktu(LocalDateTime waktu) { this.waktu = waktu; }

    public Double getSuhu() { return suhu; }
    public void setSuhu(Double suhu) { this.suhu = suhu; }

    // Helper untuk JSON response ke frontend (Chart.js & Dashboard UI)
    @JsonProperty("value")
    public Double getValue() { return suhu; }

    @JsonProperty("receivedAt")
    public LocalDateTime getReceivedAt() { return waktu; }

    @JsonProperty("sensorTopic")
    public String getSensorTopic() { return sensor != null ? sensor.getNamaSensor() : null; }

    @JsonProperty("idSensor")
    public Integer getIdSensor() { return sensor != null ? sensor.getIdSensor() : null; }
}

