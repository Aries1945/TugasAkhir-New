package com.example.TugasAkhir.mqtt;

import com.example.TugasAkhir.service.SensorDataService;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class MqttClientManagerTest {
    // Simulasikan Spring berhenti: client lama harus disconnect dan ditutup.
    @Test
    void springBerhentiMenutupClientPaho() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(true);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SensorDataService.class, () -> mock(SensorDataService.class));
            context.registerBean(MqttClientManager.class);
            context.refresh();
            MqttClientManager manager = context.getBean(MqttClientManager.class);

            // Gunakan client tiruan agar pengujian tidak membutuhkan broker.
            ReflectionTestUtils.setField(manager, "client", client);
            ReflectionTestUtils.setField(manager, "options", options);
        }

        // Menutup context di atas harus menjalankan @PreDestroy secara otomatis.
        verify(client).disconnect();
        verify(client).close(true);
        assertFalse(options.isAutomaticReconnect());
    }
}
