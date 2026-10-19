package com.cilpl.Cynergytx_admin.Activites;

import android.os.Handler;
import android.os.Looper;
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
    // Touched from the connect worker and from Paho's own callback threads.
    private volatile boolean isConnecting = false;
    private volatile boolean isConnected = false;
    private volatile boolean isSubscribed = false;
    private final Handler main = new Handler(Looper.getMainLooper());

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

        // MqttClient.connect() BLOCKS until the broker answers or the 10s connection timeout
        // expires. Running that on the main thread freezes the screen, and Android shows the
        // "app isn't responding" dialog after 5s -- exactly what a site whose network cannot
        // reach the broker (port 1883 blocked, captive portal, packet loss) would see. The
        // whole connect therefore runs on its own thread; the screen keeps playing regardless.
        final String uri = serverUri;
        Thread t = new Thread(() -> doConnect(uri, callback), "mqtt-connect");
        t.setDaemon(true);
        t.start();
    }

    /** The blocking part of connecting. Always runs off the main thread (see connect()). */
    private void doConnect(String serverUri, ConnectionCallback callback) {
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

                    // Delivered on the main thread so the caller's connection flags are only
                    // ever read and written there.
                    if (callback != null) main.post(callback::onConnected);
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.e(TAG, "MQTT connection lost", cause);

                    isConnected = false;
                    isConnecting = false;
                    isSubscribed = false;

                    if (callback != null) main.post(callback::onDisconnected);
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
            if (callback != null) main.post(() -> callback.onError(e));
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
    /**
     * Returns immediately. Paho's disconnect() blocks while it waits for the broker to
     * acknowledge, and this is called from logout and from onDestroy -- both on the main
     * thread -- so on a dead network it would freeze the screen the same way connect() did.
     * The flags are cleared here and the network part is handed to a background thread.
     */
    public synchronized void disconnect() {
        isConnecting = false;
        isConnected = false;
        isSubscribed = false;

        final MqttClient client = mqttClient;
        mqttClient = null;
        if (client == null) return;

        Thread t = new Thread(() -> {
            try {
                if (client.isConnected()) client.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Disconnect error", e);
            }
            try { client.close(); } catch (Exception ignored) { }
            Log.e(TAG, "MQTT disconnected");
        }, "mqtt-disconnect");
        t.setDaemon(true);
        t.start();
    }

    public boolean isConnected() {
        return isConnected;
    }
}
