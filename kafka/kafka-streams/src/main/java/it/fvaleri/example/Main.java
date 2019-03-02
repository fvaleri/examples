package it.fvaleri.example;

import it.fvaleri.example.model.PageView;
import it.fvaleri.example.model.Search;
import it.fvaleri.example.model.UserActivity;
import it.fvaleri.example.model.UserProfile;
import it.fvaleri.example.serde.JsonDeserializer;
import it.fvaleri.example.serde.JsonSerializer;
import it.fvaleri.example.serde.WrapperSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * This example takes 3 streams of user data: searches, clicks and profile-updates. It joins those activity streams
 * together, to generate an holistic view of user activity (record: user's location, interests, searches, clicks).
 * It uses the unique windowed-join, allowing us to match clicks with the search that happened in the same time window.
 */
public class Main {
    public static final String BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String USER_PROFILE_TOPIC = "clicks.user.profile";
    public static final String PAGE_VIEW_TOPIC = "clicks.pages.views";
    public static final String SEARCH_TOPIC = "clicks.search";
    public static final String USER_ACTIVITY_TOPIC = "clicks.user.activity";
    
    public static void main(String[] args) {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<Integer, PageView> views = builder.stream(PAGE_VIEW_TOPIC, Consumed.with(Serdes.Integer(), new PageViewSerde()));
        KTable<Integer, UserProfile> profiles = builder.table(USER_PROFILE_TOPIC, Consumed.with(Serdes.Integer(), new ProfileSerde()));
        KStream<Integer, Search> searches = builder.stream(SEARCH_TOPIC, Consumed.with(Serdes.Integer(), new SearchSerde()));

        KStream<Integer, UserActivity> viewsWithProfile = views.leftJoin(profiles,
                (page, profile) -> {
                    if (profile != null) {
                        return new UserActivity(profile.getUserId(), profile.getUserName(), profile.getZipcode(), profile.getInterests(), "", page.getPage());
                    } else {
                        return new UserActivity(-1, "", "", null, "", page.getPage());
                    }
                });

        KStream<Integer, UserActivity> userActivityStream = viewsWithProfile.leftJoin(searches,
                (userActivity, search) -> {
                    if (search != null) {
                        userActivity.updateSearch(search.getSearchTerms());
                    } else {
                        userActivity.updateSearch("");
                    }
                    return userActivity;
                },
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)),
                StreamJoined.with(Serdes.Integer(), new UserActivitySerde(), new SearchSerde()));

        userActivityStream.to(USER_ACTIVITY_TOPIC, Produced.with(Serdes.Integer(), new UserActivitySerde()));

        Topology topology = builder.build();
        System.out.println(topology.describe().toString());
        KafkaStreams streams = createKafkaStreams(topology);
        streams.setUncaughtExceptionHandler(new MyUncaughtExceptionHandler());

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
            @Override
            public void run() {
                System.out.println("Shutting down");
                if (streams != null) {
                    streams.close(Duration.ofSeconds(10));
                }
                latch.countDown();
            }
        });

        try {
            streams.cleanUp();
            streams.start();
            System.out.println("Application started, type Ctrl+C to stop");
            latch.await();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static KafkaStreams createKafkaStreams(Topology topology) {
        Properties props = new Properties();
        // application.id is the same for all instances, and must be unique in the Kafka cluster
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "clicks");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        // balance txn overhead with latency
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
        // setting offset reset to the earliest so that we can re-run the demo code with the same preloaded data
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // input topic offsets commits, state stores updates, and output topics writes completed atomically
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        // each thread will be assigned with one or more tasks (max active threads == total number of input partition)
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 2 * Runtime.getRuntime().availableProcessors());
        // if you configure N standby replicas, you need to provision N+1 KafkaStreams instances
        props.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 0);
        // serde exception handlers
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, MyDeserializationErrorHandler.class);
        props.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, MyProductionExceptionHandler.class);
        // reconnections and retries
        props.put(StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG, 50);
        props.put(StreamsConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 1_000);
        props.put(StreamsConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        return new KafkaStreams(topology, props);
    }

    public static final class PageViewSerde extends WrapperSerde<PageView> {
        public PageViewSerde() {
            super(new JsonSerializer<>(), new JsonDeserializer<>(PageView.class));
        }
    }

    public static final class ProfileSerde extends WrapperSerde<UserProfile> {
        public ProfileSerde() {
            super(new JsonSerializer<>(), new JsonDeserializer<>(UserProfile.class));
        }
    }

    public static final class SearchSerde extends WrapperSerde<Search> {
        public SearchSerde() {
            super(new JsonSerializer<>(), new JsonDeserializer<>(Search.class));
        }
    }

    static public final class UserActivitySerde extends WrapperSerde<UserActivity> {
        public UserActivitySerde() {
            super(new JsonSerializer<>(), new JsonDeserializer<>(UserActivity.class));
        }
    }

    public static class MyDeserializationErrorHandler implements DeserializationExceptionHandler {
        int errorCounter = 0;

        @Override
        public DeserializationHandlerResponse handle(ProcessorContext processorContext,
                                                     ConsumerRecord<byte[], byte[]> consumerRecord,
                                                     Exception e) {
            if (errorCounter++ < 25) {
                return DeserializationHandlerResponse.CONTINUE;
            }
            return DeserializationHandlerResponse.FAIL;
        }

        @Override
        public void configure(Map<String, ?> map) {
        }
    }

    public static class MyProductionExceptionHandler implements ProductionExceptionHandler {
        @Override
        public ProductionExceptionHandlerResponse handle(ProducerRecord<byte[], byte[]> producerRecord, Exception e) {
            if (e instanceof RecordTooLargeException) {
                return ProductionExceptionHandlerResponse.CONTINUE;
            }
            return ProductionExceptionHandlerResponse.FAIL;
        }

        @Override
        public void configure(Map<String, ?> map) {
        }
    }

    public static class MyUncaughtExceptionHandler implements StreamsUncaughtExceptionHandler {
        @Override
        public StreamThreadExceptionResponse handle(Throwable e) {
            if (e instanceof StreamsException) {
                Throwable originalException = e.getCause();
                if (originalException.getMessage().contains("Retryable")) {
                    return StreamThreadExceptionResponse.REPLACE_THREAD;
                }
            }
            return StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        }
    }
}
