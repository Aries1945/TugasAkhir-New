package com.example.TugasAkhir.model;




import jakarta.persistence.*;

@Entity
@Table(name = "sensor")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sensor")
    private Integer idSensor;

    @Column(name = "nama_sensor", nullable = false)
    private String namaSensor;

    public Sensor() {}

    public Sensor(String namaSensor) {
        this.namaSensor = namaSensor;
    }

    public Integer getIdSensor() { return idSensor; }
    public void setIdSensor(Integer idSensor) { this.idSensor = idSensor; }

    public String getNamaSensor() { return namaSensor; }
    public void setNamaSensor(String namaSensor) { this.namaSensor = namaSensor; }

    // Helper untuk kompatibilitas topik sensor
    public String getSensorTopic() { return namaSensor; }
    public void setSensorTopic(String topic) { this.namaSensor = topic; }
}

