package com.example.TugasAkhir.controller;

import com.example.TugasAkhir.mqtt.MqttClientManager;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mqtt")
public class subscribeController {

    private final MqttClientManager service;

    public subscribeController(MqttClientManager service) {
        this.service = service;
    }

    @GetMapping("/subscription-status")
    public MqttClientManager.StatusSubscription statusSubscription() {
        return service.statusSubscription();
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
            // Tangkap kegagalan subscribe yang dilaporkan library Paho.
            return ResponseEntity.status(502).body(
                    "Gagal subscribe. " + "Reason code: " + error.getReasonCode() + ". Detail Paho: " + error.toString()
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
            // Tangkap kegagalan unsubscribe yang dilaporkan library Paho.
            return ResponseEntity.status(502).body("Gagal unsubscribe. " + "Reason code: " + error.getReasonCode() + ". Detail Paho: " + error.toString());
        }
    }
}


