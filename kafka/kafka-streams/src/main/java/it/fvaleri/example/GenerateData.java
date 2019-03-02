package it.fvaleri.example;

import com.google.gson.Gson;
import it.fvaleri.example.model.PageView;
import it.fvaleri.example.model.Search;
import it.fvaleri.example.model.UserProfile;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GenerateData {
    public static KafkaProducer<Integer, String> producerInstance = null;

    public static void main(String[] args) throws Exception {
        System.out.println("Generating fake data");
        List<ProducerRecord<Integer, String>> records = new ArrayList<>();
        Gson gson = new Gson();

        try (KafkaProducer<Integer, String> producer = new KafkaProducer<>(createConfig())) {
            producerInstance = producer;

            // two users
            UserProfile user0 = new UserProfile(0, "Federico", "00148", new String[]{"coding", "running"});
            UserProfile user1 = new UserProfile(1, "Anna", "00158", new String[]{"hiking", "dancing"});
            // the User ID is the key for all events, since joins require a common key
            records.add(new ProducerRecord<>(Main.USER_PROFILE_TOPIC, user0.getUserId(), gson.toJson(user0)));
            records.add(new ProducerRecord<>(Main.USER_PROFILE_TOPIC, user1.getUserId(), gson.toJson(user1)));

            // profile update
            String[] newInterests = {"hiking", "stream processing"};
            records.add(new ProducerRecord<>(Main.USER_PROFILE_TOPIC, user1.getUserId(), gson.toJson(user1.update("00149", newInterests))));

            // two searches
            Search search0 = new Search(0, "running shorts");
            Search search1 = new Search(1, "light jacket");
            records.add(new ProducerRecord<>(Main.SEARCH_TOPIC, search0.getUserId(), gson.toJson(search0)));
            records.add(new ProducerRecord<>(Main.SEARCH_TOPIC, search1.getUserId(), gson.toJson(search1)));

            // three clicks
            PageView view0 = new PageView(0, "running-mens/shorts-5-inches");
            PageView view1 = new PageView(1, "product/dirt-craft-bike-mountain-biking-jacket");
            PageView view2 = new PageView(1, "product/ultralight-down-jacket");
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view0.getUserId(), gson.toJson(view0)));
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view1.getUserId(), gson.toJson(view1)));
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view2.getUserId(), gson.toJson(view2)));

            for (ProducerRecord<Integer, String> record : records) {
                System.out.printf("Sending record with key %d to topic %s%n", record.key(), record.topic());
                producer.send(record, (RecordMetadata r, Exception e) -> {
                    if (e != null) {
                        System.err.printf("Error producing to topic %s%n", r.topic());
                        e.printStackTrace();
                    }
                });
            }

            // sleep 5 seconds to make sure we recognize the new events as a separate session
            records.clear();
            TimeUnit.MILLISECONDS.sleep(5_000);

            // one more search
            Search search2 = new Search(1, "hiking boots");
            records.add(new ProducerRecord<>(Main.SEARCH_TOPIC, search2.getUserId(), gson.toJson(search2)));

            // two clicks
            PageView view3 = new PageView(1, "product/salomon-x-ultra-3-mid-gtx");
            PageView view4 = new PageView(1, "product/merrell-moab-2-mid-wp");
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view3.getUserId(), gson.toJson(view3)));
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view4.getUserId(), gson.toJson(view4)));

            // we want to make sure we have results for an unknown users too
            PageView view5 = new PageView(-1, "product/osprey-atmos-65-ag-pack");
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view5.getUserId(), gson.toJson(view5)));

            for (ProducerRecord<Integer, String> record : records) {
                System.out.printf("Sending record with key %d to topic %s%n", record.key(), record.topic());
                producer.send(record, (RecordMetadata r, Exception e) -> {
                    if (e != null) {
                        System.err.printf("Error producing to topic %s%n", r.topic());
                        e.printStackTrace();
                    }
                });
            }
        }
        System.out.println("DONE");
    }

    static java.util.Properties createConfig() {
        java.util.Properties config = new java.util.Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Main.BOOTSTRAP_SERVERS);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return config;
    }
}
