package com.example.TugasAkhir.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket Handler untuk Dashboard.
 *
 * Cara kerjanya sangat sederhana:
 * - Browser buka koneksi WebSocket ke /ws/suhu
 * - Handler ini simpan koneksi tersebut ke dalam Set
 * - Saat method broadcast() dipanggil, kirim pesan ke SEMUA browser
 * - Saat browser menutup halaman, koneksi dihapus dari Set
 */
@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    // Daftar semua browser yang sedang terhubung (thread-safe).
    private final Set<WebSocketSession> sesiAktif = ConcurrentHashMap.newKeySet();

    // Dipanggil otomatis saat browser baru membuka WebSocket
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sesiAktif.add(session);
        System.out.println("Dashboard terhubung via WebSocket: " + session.getId());
    }

    // Dipanggil otomatis saat browser menutup WebSocket
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sesiAktif.remove(session);
        System.out.println("Dashboard terputus: " + session.getId());
    }

    /**
     * Kirim pesan ke SEMUA browser yang sedang buka dashboard.
     * Dipanggil oleh subscriberService setiap ada data suhu baru.
     */
    public void broadcast(String jsonMessage) {
        TextMessage pesan = new TextMessage(jsonMessage);

        for (WebSocketSession sesi : sesiAktif) {
            if (sesi.isOpen()) {
                try {
                    // synchronized agar tidak kirim 2 pesan bersamaan ke 1 browser
                    synchronized (sesi) {
                        sesi.sendMessage(pesan);
                    }
                } catch (IOException e) {
                    System.out.println("Gagal kirim ke " + sesi.getId() + ": " + e.getMessage());
                }
            }
        }
    }
}
