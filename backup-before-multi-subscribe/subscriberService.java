package com.example.TugasAkhir.service;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.stereotype.Service;


@Service
public class subscriberService {

private MqttClient client;

public void koneksi( String host, int port,String clientId,String username, String password,boolean tls) throws MqttException{

    String protocol;

    if(tls){
        protocol = "ssl://";
    }else{
        protocol = "tcp://";
    }

  String brokerUrl = protocol + host + ":" + port;


  client = new MqttClient(brokerUrl, clientId, new MemoryPersistence() );

  MqttConnectionOptions options = new MqttConnectionOptions();

  options.setConnectionTimeout(10);

  //username bersifat opsional
if (username != null && !username.isEmpty()) {
    options.setUserName(username);
}
//ini untuk supaya password bersifat opsional, dan karna paho tidak bisa String jadi di convert passwordnya jadi byte[]
   
if (password != null && !password.isEmpty()) {
    options.setPassword(password.getBytes(StandardCharsets.UTF_8));
}

  client.connect(options);

}
//fungsi untuk disconncet
public void putusKoneksi() throws MqttException{

    if(client == null){ // jika belom ada client maka null
        return;
    }

    if(client.isConnected()){ // jika client masih terhubung dengan broker maka disconnect
        client.disconnect();
    }
    client.close(); // hapus resource client
    client = null;
}
public boolean cekKoneksi(){
 
    return client != null && client.isConnected();
}



public void unsubscribe(String topic) throws MqttException {
    if (!cekKoneksi()) {
        throw new IllegalStateException("Hubungkan broker terlebih dahulu.");
    }
    if (topic == null || topic.isBlank()) {
        throw new IllegalArgumentException("Topic harus diisi.");
    }
    client.unsubscribe(topic);
}

public void subscribe(String topic, int qos) throws MqttException {
    if (!cekKoneksi()) {
        throw new IllegalStateException(
            "Hubungkan broker terlebih dahulu."
        );
    }

    if (topic == null || topic.isEmpty()) {
        throw new IllegalArgumentException(
            "Topic harus diisi."
        );
    }

    if (qos < 0 || qos > 2) {
        throw new IllegalArgumentException(
            "QoS harus 0, 1, atau 2."
        );
    }

    client.subscribe(topic, qos);
}
}
