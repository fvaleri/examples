package it.fvaleri.example;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jboss.resteasy.reactive.RestForm;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import static java.lang.String.format;

@Path("/api/kafka")
@ApplicationScoped
public class KafkaResource {
    private static final Logger LOG = Logger.getLogger(Application.class);
    public static final String TOPIC_NAME = "my-topic";

    @Inject
    KafkaConsumer<String, String> consumer;

    @Inject
    KafkaProducer<String, String> producer;

    @Inject
    AdminClient admin;

    volatile boolean done = false;
    volatile String last;

    public void initialize(@Observes StartupEvent event) {
        consumer.subscribe(Collections.singleton(TOPIC_NAME));
        new Thread(() -> {
            while (!done) {
                final ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                records.forEach(record -> {
                    LOG.infof("Record({}, {}) fetched from {}-{} with offset {}",
                        record.key(), record.value(), record.topic(), record.partition(), record.offset());
                    last = format("(%s, %s)", record.key(), record.value());
                });
            }
            consumer.close();
        }).start();
    }

    public void terminate(@Observes ShutdownEvent event) {
        done = false;
        producer.close();
        admin.close();
    }

    @POST
    public long post(@RestForm String key, @RestForm String value)
            throws InterruptedException, ExecutionException, TimeoutException {
        return producer.send(new ProducerRecord<>(TOPIC_NAME, key, value)).get(5, TimeUnit.SECONDS).offset();
    }

    @GET
    public String getLast() {
        return last;
    }

    @Path("/topics")
    @GET
    public Set<String> getTopics() throws InterruptedException, ExecutionException, TimeoutException {
        return admin.listTopics().names().get(5, TimeUnit.SECONDS);
    }
}
