package com.example.TugasAkhir.controller;

import com.example.TugasAkhir.mqtt.MqttClientManager;
import java.net.UnknownHostException;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttControllerTest {
    // Simulasikan kegagalan tanpa menghubungi broker sungguhan.
    @Test
    void errorNolMenampilkanPenyebabAsli() throws Exception {
        MqttClientManager manager = mock(MqttClientManager.class);
        MqttException error = new MqttException(new UnknownHostException("broker.invalid"));
        doThrow(error).when(manager).koneksi("broker.invalid", 1883, "uji", null, null, false);
        mqttController controller = new mqttController(manager);

        var response = controller.connect("broker.invalid", 1883, "uji", null, null, false);

        assertEquals(502, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Reason code: 0"));
        assertTrue(response.getBody().contains("UnknownHostException"));
        assertTrue(response.getBody().contains("broker.invalid"));
    }
}
