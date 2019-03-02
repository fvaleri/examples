package it.fvaleri.example;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import static it.fvaleri.example.Configuration.MESSAGE_QOS;
import static it.fvaleri.example.Configuration.MESSAGE_RETAINED;
import static it.fvaleri.example.Configuration.MESSAGE_SIZE_BYTES;
import static it.fvaleri.example.Configuration.NUM_MESSAGES;
import static it.fvaleri.example.Configuration.PROCESSING_DELAY_MS;
import static it.fvaleri.example.Configuration.TOPIC_NAME;

public class Producer extends Client {
    private static IMqttClient client;

    public Producer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        client = connect();
        MqttMessage message = new MqttMessage(randomBytes(MESSAGE_SIZE_BYTES));
        message.setQos(MESSAGE_QOS);
        message.setRetained(MESSAGE_RETAINED);
        while (!closed.get() && messageCount.get() < NUM_MESSAGES) {
            sleep(PROCESSING_DELAY_MS);
            client.publish(TOPIC_NAME, message);
            messageCount.incrementAndGet();
            LOG.debug("Message sent");
        }
    }

    @Override
    public void onShutdown() {
        try {
            client.disconnect();
            client.close();
        } catch (Throwable e) {
        }
    }
}
