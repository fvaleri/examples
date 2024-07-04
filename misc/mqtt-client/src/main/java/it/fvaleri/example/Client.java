package it.fvaleri.example;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Client extends Thread {
    private static final Random RND = new Random();

    protected AtomicLong messageCount = new AtomicLong(0);
    protected AtomicBoolean closed = new AtomicBoolean(false);

    public Client(String threadName) {
        super(threadName);
    }

    @Override
    public void run() {
        try {
            System.out.println("Starting up");
            execute();
            shutdown(null);
        } catch (Throwable e) {
            System.err.println("Unhandled exception");
            shutdown(e);
        }
    }

    public void shutdown(Throwable e) {
        if (!closed.get()) {
            System.out.println("Shutting down");
            closed.set(true);
            onShutdown();
            if (e != null) {
                e.printStackTrace();
                System.exit(1);
            } else {
                System.exit(0);
            }
        }
    }

    // implement the execution loop
    abstract void execute() throws Exception;

    // override if custom shutdown logic is needed
    void onShutdown() {
    }

    byte[] randomBytes(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Record size must be greater than zero");
        }
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; ++i) {
            payload[i] = (byte) (RND.nextInt(26) + 65);
        }
        return payload;
    }

    IMqttClient connect() throws Exception {
        if (Configuration.SSL_TRUSTSTORE_LOCATION != null) {
            System.setProperty("javax.net.ssl.trustStore", Configuration.SSL_TRUSTSTORE_LOCATION);
            System.setProperty("javax.net.ssl.trustStorePassword", Configuration.SSL_TRUSTSTORE_PASSWORD);
            if (Configuration.SSL_KEYSTORE_LOCATION != null) {
                System.setProperty("javax.net.ssl.keyStore", Configuration.SSL_KEYSTORE_LOCATION);
                System.setProperty("javax.net.ssl.keyStorePassword", Configuration.SSL_KEYSTORE_PASSWORD);
            }
        }
        // using synchronous implementation
        String[] urls = Configuration.CONNECTION_URL.split(",");
        IMqttClient client = new MqttClient(urls[0], Configuration.CLIENT_ID, new MqttDefaultFilePersistence("/tmp"));
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(urls);
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        if (Configuration.CONNECTION_USERNAME != null && Configuration.CONNECTION_PASSWORD != null) {
            options.setUserName(Configuration.CONNECTION_USERNAME);
            options.setPassword(Configuration.CONNECTION_PASSWORD.toCharArray());
        }
        client.connect(options);
        return client;
    }
}
