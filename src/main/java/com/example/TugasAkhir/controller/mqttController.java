package com.example.TugasAkhir.controller;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.TugasAkhir.service.PesanErrorMqtt;
import com.example.TugasAkhir.mqtt.MqttClientManager;

@RestController
@RequestMapping("/api/mqtt")
public class mqttController {

    private final MqttClientManager service;

    // Spring memberikan objek service kepada controller.
    public mqttController(MqttClientManager service) {
        this.service = service;
    }

    // Menerima permintaan POST ke /api/mqtt/connect.
    @PostMapping("/connect")
    public ResponseEntity<String> connect(
            @RequestParam("host") String host,
            @RequestParam("port") int port,
            @RequestParam("clientId") String clientId,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam("tls") boolean tls) {

        try {
            // Periksa input juga di Java sebelum menghubungi broker.
            if (host.isBlank()) {
                return ResponseEntity.badRequest().body("Host broker harus diisi.");
            }
            if (port < 1 || port > 65535) {
                return ResponseEntity.badRequest().body("Port harus antara 1 dan 65535.");
            }
            if (clientId.isBlank()) {
                return ResponseEntity.badRequest().body("Client ID harus diisi dan tidak boleh hanya spasi.");
            }
            host = host.trim();
            // Java memeriksa format alamat sebelum alamat diberikan ke Paho.
            URI alamat = new URI("tcp://" + host + ":" + port);
            if (alamat.getHost() == null) {
                return ResponseEntity.badRequest().body("Format nama broker atau IP tidak valid.");
            }
            if (alamat.getUserInfo() != null) {
                return ResponseEntity.badRequest().body("Username tidak boleh ditulis di field host.");
            }
            if (alamat.getPort() != port) {
                return ResponseEntity.badRequest().body("Tulis port di field Port, bukan di Host.");
            }
            if (!alamat.getRawPath().isEmpty()) {
                return ResponseEntity.badRequest().body("Host tidak boleh berisi protokol atau path.");
            }
            if (alamat.getQuery() != null || alamat.getFragment() != null) {
                return ResponseEntity.badRequest().body("Host tidak boleh berisi tanda ? atau #.");
            }
            // Minta service menghubungkan aplikasi ke broker.
            service.koneksi(
                    host,
                    port,
                    clientId,
                    username,
                    password,
                    tls);

            // Kirim jawaban berhasil ke JavaScript.
            return ResponseEntity.ok(
                    "Berhasil terhubung ke broker");

        } catch (MqttException error) {
            // Paho memberi tahu connect gagal. Kirim penjelasannya ke halaman web.
            return ResponseEntity.status(502).body("Gagal connect. " + PesanErrorMqtt.dariException(error));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        } catch (URISyntaxException error) {
            return ResponseEntity.badRequest().body("Konfigurasi tidak valid. Periksa format host dan Client ID.");
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<String> disconnect() {

        try {
            // Minta service memutus koneksi.
            service.putusKoneksi();

            return ResponseEntity.ok("Koneksi berhasil diputus");

        } catch (MqttException error) {
            // Paho memberi tahu disconnect gagal. Tampilkan pesan untuk pengguna.
            return ResponseEntity.status(500).body(
                    "Gagal disconnect. " + PesanErrorMqtt.dariException(error));
        }
    }

    @GetMapping("/status")
    public boolean status() {
        return service.cekKoneksi();
    }
}
