package com.example.TugasAkhir.repository;

import com.example.TugasAkhir.model.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

// Repository untuk mengakses data master sensor di database.
@Repository
public interface SensorRepository extends JpaRepository<Sensor, Integer> {
    Optional<Sensor> findByNamaSensor(String namaSensor);
    boolean existsByNamaSensor(String namaSensor);
}
