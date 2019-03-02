package it.fvaleri.example;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);
    private static final Properties PROPS = loadConfigurationFile();
    private static final Map<String, String> CONFIG = new TreeMap<>();

    public static final String CLIENT_TYPE = getOrDefault("client.type", "producer");
    public static final int MESSAGE_SIZE_BYTES = getOrDefault("message.size.bytes", 100, Integer::parseInt);
    public static final long NUM_MESSAGES = getOrDefault("num.messages", 100L, Long::parseLong);
    public static final long PROCESSING_DELAY_MS = getOrDefault("processing.delay.ms", 0L, Long::parseLong);

    public static final String CLIENT_ID = getOrDefault("client.id", "client-" + UUID.randomUUID());
    public static final String TOPIC_NAME = getOrDefault("topic.name", "my-topic");
    public static final boolean MESSAGE_RETAINED = getOrDefault("message.retained", true, Boolean::parseBoolean);
    public static final int MESSAGE_QOS = getOrDefault("message.qos", 1, Integer::parseInt);

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
            k.toLowerCase(Locale.ROOT).contains("password") && v != null ? "*****" : v));
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
}
