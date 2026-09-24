package com.example.TugasAkhir.config;

import com.example.TugasAkhir.websocket.DashboardWebSocketHandler;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Konfigurasi WebSocket.
 *
 * Mendaftarkan endpoint /ws/suhu supaya browser bisa
 * membuka koneksi WebSocket ke alamat itu.
 *
 * Contoh: ws://localhost:8080/ws/suhu
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DashboardWebSocketHandler handler;

    public WebSocketConfig(DashboardWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Daftarkan handler di path /ws/suhu, izinkan semua origin
        registry.addHandler(handler, "/ws/suhu").setAllowedOrigins("*");
    }
}
