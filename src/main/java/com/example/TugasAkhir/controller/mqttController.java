package com.example.TugasAkhir.controller;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.TugasAkhir.service.subscriberService;

@RestController
@RequestMapping("/api/mqtt")
public class mqttController {

    private final subscriberService service;

    // Spring memberikan objek service kepada controller.
    public mqttController(subscriberService service) {
        this.service = service;
    }

    // Menerima permintaan POST ke /api/mqtt/connect.
    @PostMapping("/connect")
    public ResponseEntity<String> connect(
            @RequestParam("host") String host,
            @RequestParam("port") int port,
            @RequestParam("clientId") String clientId,
            @RequestParam(value = "username", required = false)
            String username,
            @RequestParam(value = "password", required = false)
            String password,
            @RequestParam("tls") boolean tls
    ) {

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
                    tls
            );

            // Kirim jawaban berhasil ke JavaScript.
            return ResponseEntity.ok(
                    "Berhasil terhubung ke broker"
            );

        } catch (MqttException error) {
            return ResponseEntity.status(502).body(pesanErrorKoneksi(error));
        } catch (URISyntaxException | IllegalArgumentException error) {
            return ResponseEntity.badRequest().body("Konfigurasi tidak valid. Periksa format host dan Client ID.");
        }
    }

    private String pesanErrorKoneksi(MqttException error) {
        // Error Paho bisa membungkus error lain, misalnya nama broker tidak ditemukan.
        Throwable penyebab = error;
        // Baca penyebabnya satu per satu sampai ditemukan pesan yang sesuai.
        while (penyebab != null) {
            if (penyebab instanceof UnknownHostException) {
                return "Nama broker tidak dapat ditemukan. Periksa ejaan host, koneksi internet, atau DNS.";
            }
            if (penyebab instanceof SSLException) {
                return "Koneksi TLS gagal. Periksa port TLS dan sertifikat broker.";
            }
            if (penyebab instanceof ConnectException) {
                return "Koneksi ke broker ditolak atau tidak dapat dibuat. Periksa host, port, layanan broker, dan jaringan.";
            }
            if (penyebab instanceof SocketTimeoutException) {
                return "Broker tidak merespons tepat waktu. Periksa host, port, dan jaringan.";
            }
            penyebab = penyebab.getCause();
        }
        if (error.getReasonCode() == 138) {
            return "Koneksi ditolak broker: Client ID atau IP Anda diblokir (kode 138 / Banned). Coba ganti Client ID menjadi nama unik (misal: " + "client-" + System.currentTimeMillis() + ") dan kosongkan username/password untuk broker publik.";
        }
        if (error.getReasonCode() == 134 || error.getReasonCode() == 135) {
            return "Koneksi ditolak broker: Autentikasi/Otorisasi gagal (kode " + error.getReasonCode() + "). Periksa username dan password Anda.";
        }

        return "Koneksi MQTT gagal (kode " + error.getReasonCode()
                + "). Periksa port, username/password, izin akun, dan pilihan TLS.";
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<String> inputTidakValid() {
        return ResponseEntity.badRequest().body("Lengkapi field wajib. Port harus angka bulat 1–65535 dan keamanan koneksi harus dipilih.");
    }


    @PostMapping("/disconnect")
public ResponseEntity<String> disconnect() {

    try {
        // Minta service memutus koneksi.
        service.putusKoneksi();

        return ResponseEntity.ok("Koneksi berhasil diputus");

    } catch (MqttException error) {
        return ResponseEntity.status(500).body(
                "Gagal memutus koneksi"
        );
    }
}


@GetMapping("/status")
public boolean status() {
    return service.cekKoneksi();
}
}


