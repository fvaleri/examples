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
    public static final long NUM_MESSAGES = getOrDefault("num.messages", Long.MAX_VALUE, Long::parseLong);
    public static final long PROCESSING_DELAY_MS = getOrDefault("processing.delay.ms", 0L, Long::parseLong);
    public static final long POLL_TIMEOUT_MS = getOrDefault("poll.timeout.ms", 1_000L, Long::parseLong);
    public static final long MAX_RETRIES = getOrDefault("max.retries", 5, Integer::parseInt);
    public static final long RETRY_BACKOFF_MS = getOrDefault("retry.backoff.ms", 5_000L, Long::parseLong);

    public static final String BOOTSTRAP_SERVERS = getOrDefault("bootstrap.servers", null);
    public static final String CLIENT_ID = getOrDefault("client.id", "client-" + UUID.randomUUID());
    public static final String SECURITY_PROTOCOL = getOrDefault("security.protocol", "PLAINTEXT");
    public static final String TOPIC_NAME = getOrDefault("topic.name", "my-topic");
    public static final String GROUP_ID = getOrDefault("group.id", "my-group");

    public static final String ADMIN_CONFIG = getOrDefault("admin.config", null);
    public static final String PRODUCER_CONFIG = getOrDefault("producer.config", null);
    public static final String CONSUMER_CONFIG = getOrDefault("consumer.config", null);

    public static final String SSL_TRUSTSTORE_TYPE = getOrDefault("ssl.truststore.type", "PKCS12");
    public static final String SSL_TRUSTSTORE_LOCATION = getOrDefault("ssl.truststore.location", null);
    public static final String SSL_TRUSTSTORE_PASSWORD = getOrDefault("ssl.truststore.password", null);
    public static final String SSL_KEYSTORE_TYPE= getOrDefault("ssl.keystore.type", "PKCS12");
    public static final String SSL_KEYSTORE_LOCATION = getOrDefault("ssl.keystore.location", null);
    public static final String SSL_KEYSTORE_PASSWORD = getOrDefault("ssl.keystore.password", null);

    public static final String SASL_MECHANISM = getOrDefault("sasl.mechanism", null);
    public static final String SASL_USERNAME = getOrDefault("sasl.username", null);
    public static final String SASL_PASSWORD = getOrDefault("sasl.password", null);
    public static final String SASL_OAUTH_TOKEN_ENDPOINT_URI = getOrDefault("sasl.oauth.token.endpoint.uri", null);
    public static final String SASL_OAUTH_CLIENT_ID = getOrDefault("sasl.oauth.client.id", null);
    public static final String SASL_OAUTH_CLIENT_SECRET = getOrDefault("sasl.oauth.client.secret", null);

    public static final String KUBE_NAMESPACE = getOrDefault("kube.namespace", null);
    public static final String KUBE_CLUSTER = getOrDefault("kube.cluster", null);
    public static final String KUBE_USER = getOrDefault("kube.user", null);

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
