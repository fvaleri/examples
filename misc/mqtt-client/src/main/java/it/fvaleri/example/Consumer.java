package it.fvaleri.example;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.concurrent.CountDownLatch;

import static it.fvaleri.example.Configuration.NUM_MESSAGES;

public class Consumer extends Client implements IMqttMessageListener {
    private static IMqttClient client;
    private static CountDownLatch latch;

    public Consumer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        client = connect();
        client.subscribe(Configuration.TOPIC_NAME, this);
        System.out.printf("Subscribed to %s%n", Configuration.TOPIC_NAME);
        while (!closed.get() && messageCount.get() < NUM_MESSAGES) {
            // wait indefinitely for new messages
            latch = new CountDownLatch(1);
            latch.await();
        }
    }

    @Override
    public void onShutdown() {
        try {
            client.disconnect();
            client.close();
        } catch (Throwable e) {
        }
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            System.out.println("Message received");
            sleep(Configuration.PROCESSING_DELAY_MS);
            if (messageCount.incrementAndGet() == NUM_MESSAGES) {
                shutdown(null);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
