package it.fvaleri.example;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import javax.jms.DeliveryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);
    private static final Properties PROPS = loadConfigurationFile();
    private static final Map<String, String> CONFIG = new TreeMap<>();

    public static final String CLIENT_TYPE = getOrDefault("client.type", "producer");
    public static final String PROTOCOL_NAME = getOrDefault("protocol.name", "core");
    public static final int MESSAGE_SIZE_BYTES = getOrDefault("message.size.bytes", 100, Integer::parseInt);
    public static final long NUM_MESSAGES = getOrDefault("num.messages", Long.MAX_VALUE, Long::parseLong);
    public static final long PROCESSING_DELAY_MS = getOrDefault("processing.delay.ms", 0L, Long::parseLong);
    public static final long RECEIVE_TIMEOUT_MS = getOrDefault("receive.timeout.ms", 10_000L, Long::parseLong);
    public static final boolean ENABLE_TXN = getOrDefault("enable.txn", false, Boolean::parseBoolean);
    public static final int TXN_BATCH_MSGS = getOrDefault("txn.batch.msgs", 100, Integer::parseInt);

    public static final String CLIENT_ID = getOrDefault("client.id", "client-" + UUID.randomUUID());
    public static final String QUEUE_NAME = getOrDefault("queue.name", null);
    public static final String TOPIC_NAME = getOrDefault("topic.name", null);
    public static final String SUBSCRIPTION_NAME = getOrDefault("subscription.name", null);
    public static final int MESSAGE_DELIVERY = getOrDefault("message.delivery", DeliveryMode.PERSISTENT, Integer::parseInt);
    public static final int MESSAGE_PRIORITY = getOrDefault("message.priority", 4, Integer::parseInt);
    public static final String MESSAGE_SELECTOR = getOrDefault("message.selector", null);
    public static final long MESSAGE_TTL_MS = getOrDefault("message.ttl.ms", 0L, Long::parseLong);

    public static final String CONNECTION_URL = getOrDefault("connection.url", null);
    public static final String CONNECTION_USERNAME = getOrDefault("connection.username", null);
    public static final String CONNECTION_PASSWORD = getOrDefault("connection.password", null);

    public static final String SSL_TRUSTSTORE_LOCATION = getOrDefault("ssl.truststore.location", null);
    public static final String SSL_TRUSTSTORE_PASSWORD = getOrDefault("ssl.truststore.password", null);
    public static final String SSL_KEYSTORE_LOCATION = getOrDefault("ssl.keystore.location", null);
    public static final String SSL_KEYSTORE_PASSWORD = getOrDefault("ssl.keystore.password", null);

    private Configuration() {
    }

    static {
        LOG.info("=======================================================");
        CONFIG.forEach((k, v) -> LOG.info("{}: {}", k,
            (contains(k, "password", "keystore.key") && v != null) ? "*****" : v));
        LOG.info("=======================================================");
    }

    private static Properties loadConfigurationFile() {
        Properties prop = new Properties();
        try {
            prop.load(Configuration.class.getClassLoader().getResourceAsStream("application.properties"));
            return prop;
        } catch (IOException e) {
            throw new RuntimeException("Load configuration error", e);
        }
    }

    private static String getOrDefault(String key, String defaultValue) {
        return getOrDefault(key, defaultValue, String::toString);
    }

    private static <T> T getOrDefault(String key, T defaultValue, Function<String, T> converter) {
        String envKey = key != null ? key.toUpperCase(Locale.ENGLISH).replaceAll("\\.", "_") : null;
        String value = System.getenv(envKey) != null ? System.getenv(envKey) :
            (Objects.requireNonNull(PROPS).get(key) != null ? PROPS.getProperty(key) : null);
        T returnValue = defaultValue;
        if (value != null) {
            returnValue = converter.apply(value);
        }
        CONFIG.put(key, String.valueOf(returnValue));
        return returnValue;
    }

    private static boolean contains(String key, String... words) {
        for (String word : words) {
            if (key.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
