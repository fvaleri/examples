package it.fvaleri.example;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Client extends Thread {
    protected static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static final Random RND = new Random();

    protected AtomicLong messageCount = new AtomicLong(0);
    protected AtomicBoolean closed = new AtomicBoolean(false);

    public Client(String threadName) {
        super(threadName);
    }

    @Override
    public void run() {
        try {
            LOG.info("Starting up");
            execute();
            shutdown(null);
        } catch (Throwable e) {
            LOG.error("Unhandled exception");
            shutdown(e);
        }
    }

    public void shutdown(Throwable e) {
        if (!closed.get()) {
            LOG.info("Shutting down");
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
