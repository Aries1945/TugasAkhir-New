package com.example.TugasAkhir.mqtt;

import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MqttCallbackHandlerTest {
    // Tidak ada pemrosesan suhu pada pengujian status, sehingga service tidak diperlukan.
    private final MqttCallbackHandler callback = new MqttCallbackHandler(null);

    // Alasan dari broker tidak boleh diganti dengan pesan terjemahan aplikasi.
    @Test
    void alasanBrokerTetapAsli() {
        callback.disconnected(new MqttDisconnectResponse(142, "Session taken over", null, null));
        assertTrue(callback.ambilPesan().contains("Reason code: 142"));
        assertTrue(callback.ambilPesan().contains("Reason string: Session taken over"));
    }

    // Broker boleh tidak menyertakan reason string.
    @Test
    void alasanKosongTidakMenampilkanNull() {
        callback.disconnected(new MqttDisconnectResponse(0, null, null, null));
        assertTrue(callback.ambilPesan().contains("Reason code: 0"));
        assertFalse(callback.ambilPesan().contains("null"));
        assertFalse(callback.ambilPesan().contains("Reason string:"));
    }

    // Kehilangan jaringan harus memakai kode exception, bukan return code default nol.
    @Test
    void jaringanPutusMemakaiExceptionPaho() {
        MqttException error = new MqttException(32109);
        callback.disconnected(new MqttDisconnectResponse(error));
        assertTrue(callback.ambilPesan().contains("Reason code: 32109"));
        assertTrue(callback.ambilPesan().contains(error.getMessage()));
    }

    // Hasil subscribe sebelum koneksi terputus tidak boleh mengaktifkan status kembali.
    @Test
    void hasilSubscribeLamaTetapDiabaikan() {
        int nomor = callback.ambilNomorKoneksi();
        callback.disconnected(new MqttDisconnectResponse(new MqttException(32109)));
        callback.selesaiSubscribe(nomor);
        assertFalse(callback.sudahSubscribe());
    }
}
