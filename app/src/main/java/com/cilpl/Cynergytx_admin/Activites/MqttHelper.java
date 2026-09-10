package com.cilpl.Cynergytx_admin.Activites;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.UUID;

public class MqttHelper {

    private static final String TAG = "MQTTHelper";

//    private final String serverUri = "tcp://test.mosquitto.org:1883";
    private  String serverUri;

    private final String clientId = "AndroidTV-" + UUID.randomUUID();

    private MqttClient mqttClient;
    private final MqttListener mqttListener;

    private String dynamicTopic;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private boolean isSubscribed = false;

    public interface MqttListener {
        void onMqttMessage(String topic, String message);
    }
    public void setServerUrl(String url) {
        this.serverUri = url;
    }
    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(Throwable e);
    }

    public MqttHelper(MqttListener listener) {
        this.mqttListener = listener;
    }

    public void setTopic(String topic) {
        this.dynamicTopic = topic;
//        this.isSubscribed = false; // force re-subscribe on topic change
    }

    // =====================================================
    // CONNECT (SAFE)
    // =====================================================
    public synchronized void connect(ConnectionCallback callback) {
        if (serverUri == null || serverUri.isEmpty()) {
            Log.e(TAG, "MQTT serverUri is NULL");
            return;
        }
        if (isConnected || isConnecting) {
            Log.w(TAG, "Already connected or connecting");
            return;
        }

        isConnecting = true;

        try {
            mqttClient = new MqttClient(serverUri, clientId, null);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            mqttClient.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.e(TAG, "MQTT connected (reconnect=" + reconnect + ")"+","+serverURI);

                    isConnected = true;
                    isConnecting = false;

                    if (!isSubscribed && dynamicTopic != null) {
                        subscribeToTopic(dynamicTopic);
                    }

                    if (callback != null) callback.onConnected();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.e(TAG, "MQTT connection lost", cause);

                    isConnected = false;
                    isConnecting = false;
                    isSubscribed = false;

                    if (callback != null) callback.onDisconnected();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    if (mqttListener != null) {
                        mqttListener.onMqttMessage(topic, message.toString());
                    }
//                    if(message.equals("CALL_API")){
//
//                    }else {
//                        Log.e("mqttupdate",message.toString()+"--1");
//                    }

                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            mqttClient.connect(options);

        } catch (Exception e) {
            isConnected = false;
            isConnecting = false;
            Log.e(TAG, "MQTT connect error", e);
            if (callback != null) callback.onError(e);
        }
    }


    public synchronized void changeServer(String newServerUrl,
                                          ConnectionCallback callback) {

        try {
            Log.e(TAG, "Switching MQTT server to: " + newServerUrl);

            // 1️⃣ Disconnect old
            if (mqttClient != null) {
                try {
                    mqttClient.disconnect();
                    mqttClient.close();
                } catch (Exception ignored) {}
            }

            mqttClient = null;
            isConnected = false;
            isConnecting = false;

            // 2️⃣ Update URL
            this.serverUri = newServerUrl;

            // 3️⃣ Reconnect
            connect(callback);

        } catch (Exception e) {
            Log.e(TAG, "Error switching MQTT server", e);
        }
    }

    // =====================================================
    // SUBSCRIBE (ONCE)
    // =====================================================
    private synchronized void subscribeToTopic(String topic) {
        try {
            if (mqttClient != null && mqttClient.isConnected() && !isSubscribed) {

                mqttClient.subscribe(topic, 1);
                isSubscribed = true;

                Log.e(TAG, "Subscribed to topic: " + topic);
            }
        } catch (MqttException e) {
            Log.e(TAG, "Subscribe error", e);
        }
    }

    // =====================================================
    // PUBLISH
    // =====================================================
    public void publish(String topic, String payload) {
        if (!isConnected || mqttClient == null) return;

        try {
            mqttClient.publish(topic, new MqttMessage(payload.getBytes()));
        } catch (Exception e) {
            Log.e(TAG, "Publish error", e);
        }
    }

    // =====================================================
    // DISCONNECT (SAFE)
    // =====================================================
    public synchronized void disconnect() {
        try {
            isConnecting = false;
            isConnected = false;
            isSubscribed = false;

            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }

            mqttClient = null;
            Log.e(TAG, "MQTT disconnected");

        } catch (Exception e) {
            Log.e(TAG, "Disconnect error", e);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
