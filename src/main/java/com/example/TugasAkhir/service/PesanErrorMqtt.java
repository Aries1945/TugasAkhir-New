package com.example.TugasAkhir.service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.eclipse.paho.mqttv5.common.MqttException;

// Ubah error teknis menjadi petunjuk singkat untuk pengguna.
public class PesanErrorMqtt {
    public static String dariException(MqttException error) {
        Throwable penyebab = error;

        // Error Paho kadang membungkus error jaringan. Periksa penyebabnya juga.
        for (int i = 0; penyebab != null && i < 10; i++) {
            if (penyebab instanceof UnknownHostException) {
                return "Nama broker tidak ditemukan. Periksa Host dan koneksi internet.";
            }
            if (penyebab instanceof SSLException) {
                return "Koneksi TLS gagal. Periksa port TLS dan sertifikat broker.";
            }
            if (penyebab instanceof SocketTimeoutException) {
                return "Broker belum merespons. Periksa jaringan, Host, dan Port, lalu coba lagi.";
            }
            if (penyebab instanceof ConnectException) {
                return "Broker tidak dapat dihubungi. Periksa Host, Port, dan apakah broker sedang aktif.";
            }
            penyebab = penyebab.getCause();
        }
        return dariKode(error.getReasonCode());
    }

    public static String dariKode(int kode) {
        // Angka kecil adalah kode MQTT v5; angka 32000-an adalah kode client Paho.
        switch (kode) {
            case 133:
                return "Client ID ditolak. Periksa format Client ID.";
            case 134:
                return "Username atau password ditolak. Periksa kembali data login.";
            case 135:
                return "Akses ditolak broker. Periksa izin akun dan topic.";
            case 136:
            case 137:
                return "Broker sedang tidak tersedia atau sibuk. Coba lagi nanti.";
            case 138:
                return "Akses diblokir broker. Hubungi pengelola broker.";
            case 141:
                return "Batas keep-alive terlewati. Periksa kestabilan jaringan.";
            case 142:
                return "Client ID dipakai koneksi lain. Gunakan Client ID berbeda untuk setiap aplikasi. (Kode 142)";
            case 143:
            case 144:
                return "Topic ditolak. Periksa penulisan topic dan tanda wildcard + atau #.";
            case 151:
                return "Batas penggunaan broker tercapai. Kurangi permintaan atau hubungi pengelola broker.";
            case 155:
                return "QoS tidak didukung broker. Coba QoS yang lebih rendah.";
            case 32000:
                return "Waktu tunggu habis. Periksa jaringan dan broker, lalu coba lagi.";
            case 32103:
                return "Koneksi ke broker gagal. Periksa Host, Port, dan jaringan.";
            case 32104:
                return "Belum terhubung ke broker. Hubungkan broker terlebih dahulu.";
            case 32109:
                return "Koneksi ke broker hilang. Periksa jaringan dan ketersediaan broker.";
            default:
                return "Permintaan MQTT gagal (kode " + kode + "). Periksa pengaturan dan log aplikasi.";
        }
    }
}
