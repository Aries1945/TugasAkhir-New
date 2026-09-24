package com.example.TugasAkhir.controller;

import com.example.TugasAkhir.service.subscriberService;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mqtt")
public class subscribeController {

    private final subscriberService service;

    public subscribeController(subscriberService service) {
        this.service = service;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(
            @RequestParam("topic") String topic,
            @RequestParam("qos") int qos
    ) {
        try {
            service.subscribe(topic, qos);

            return ResponseEntity.ok(
                    "Berhasil subscribe ke topic: " + topic
            );

        } catch (IllegalStateException error) {
            return ResponseEntity.status(409).body(
                    error.getMessage()
            );

        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(
                    error.getMessage()
            );

        } catch (MqttException error) {
            return ResponseEntity.status(502).body(
                    "Gagal subscribe. Periksa koneksi dan izin topic."
            );
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam("topic") String topic) {
        try {
            service.unsubscribe(topic);
            return ResponseEntity.ok("Berhasil unsubscribe dari topic: " + topic);
        } catch (IllegalStateException error) {
            return ResponseEntity.status(409).body(error.getMessage());
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        } catch (MqttException error) {
            return ResponseEntity.status(502).body("Gagal unsubscribe. Periksa koneksi broker.");
        }
    }
}
