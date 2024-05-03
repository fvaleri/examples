package it.fvaleri.example;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.errors.RebalanceInProgressException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public abstract class Client extends Thread {
    protected static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static final Random RND = new Random(0);

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
    abstract void execute();

    // override when custom shutdown logic is needed
    void onShutdown() {
    }
    
    void sleepFor(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // the API is evolving, so this method does not include all fatal errors
    // additionally, you may want to add your business logic errors
    boolean retriable(Exception e) {
        if (e == null) {
            return false;
        } else if (e instanceof IllegalArgumentException
            || e instanceof UnsupportedOperationException
            || e instanceof UnsupportedVersionException
            || !(e instanceof RebalanceInProgressException)
            || !(e instanceof RetriableException)) {
            // non retriable exception
            return false;
        } else {
            // retriable
            return true;
        }
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

    void createTopics(String... topicNames) {
        createTopics(Configuration.BOOTSTRAP_SERVERS, -1, topicNames);
    }

    void createTopics(String bootstrapServers, int numPartitions, String... topicNames) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "client" + UUID.randomUUID());
        addConfig(props, Configuration.ADMIN_CONFIG);
        addSecurityConfig(props);
        try (Admin admin = Admin.create(props)) {
            // use default RF to avoid NOT_ENOUGH_REPLICAS error with minISR>1
            short replicationFactor = -1;
            List<NewTopic> newTopics = Arrays.stream(topicNames)
                .map(name -> new NewTopic(name, numPartitions, replicationFactor))
                .collect(Collectors.toList());
            try {
                admin.createTopics(newTopics).all().get();
                LOG.info("Created topics: {}", Arrays.toString(topicNames));
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    void addConfig(Properties props, String config) {
        Properties addProps = new Properties();
        if (config != null)   {
            try {
                props.load(new StringReader(config.replace(" ", "\n")));
            } catch (IOException | IllegalArgumentException e)   {
                throw new IllegalArgumentException("Failed to parse configuration");
            }
        }
        props.putAll(addProps);
    }
    
    void addSecurityConfig(Properties props) {
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, Configuration.SECURITY_PROTOCOL);
        if (Configuration.SSL_HOSTNAME_VERIFICATION)   {
            props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");
        } else {
            props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
        }
        if (Configuration.SSL_TRUSTSTORE_TYPE != null) {
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, Configuration.SSL_TRUSTSTORE_TYPE);
            switch (Configuration.SSL_TRUSTSTORE_TYPE) {
                case "PEM":
                    props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, Configuration.SSL_TRUSTSTORE_CERTIFICATES);
                    break;
                case "PKCS12":
                case "JKS":
                    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, Configuration.SSL_TRUSTSTORE_LOCATION);
                    props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, Configuration.SSL_TRUSTSTORE_PASSWORD);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported truststore type");
            }
        }
        if (Configuration.SSL_KEYSTORE_TYPE != null) {
            props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, Configuration.SSL_KEYSTORE_TYPE);
            switch (Configuration.SSL_KEYSTORE_TYPE) {
                case "PEM":
                    props.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, Configuration.SSL_KEYSTORE_CERTIFICATE_CHAIN);
                    props.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, Configuration.SSL_KEYSTORE_KEY);
                    break;
                case "PKCS12":
                case "JKS":
                    props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, Configuration.SSL_KEYSTORE_LOCATION);
                    props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, Configuration.SSL_KEYSTORE_PASSWORD);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported keystore type");
            }
        }
        if (Configuration.SASL_MECHANISM != null) {
            props.put(SaslConfigs.SASL_MECHANISM, Configuration.SASL_MECHANISM);
            switch (Configuration.SASL_MECHANISM) {
                case "PLAIN":
                    props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslPlainJaasConfig());
                    break;
                case "SCRAM-SHA-512":
                    props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslScramJaasConfig());
                    break;
                case "OAUTHBEARER":
                    props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslOauthJaasConfig());
                    props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                        "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported SASL mechanism");
            }
        }
    }

    String getSaslPlainJaasConfig() {
        String jaasConfig = format("org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"%s\" password=\"%s\";", Configuration.SASL_USERNAME, Configuration.SASL_PASSWORD);
        return jaasConfig;
    }

    String getSaslScramJaasConfig() {
        String jaasConfig = format("org.apache.kafka.common.security.scram.ScramLoginModule required " +
            "username=\"%s\" password=\"%s\";", Configuration.SASL_USERNAME, Configuration.SASL_PASSWORD);
        return jaasConfig;
    }

    String getSaslOauthJaasConfig() {
        String jaasConfig = format("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                "oauth.client.id=\"%s\" oauth.client.secret=\"%s\" oauth.token.endpoint.uri=\"%s\" " +
                "oauth.ssl.truststore.location=\"%s\" oauth.ssl.truststore.password=\"%s\" oauth.ssl.truststore.type=\"%s\";",
            Configuration.SASL_OAUTH_CLIENT_ID,
            Configuration.SASL_OAUTH_CLIENT_SECRET,
            Configuration.SASL_OAUTH_TOKEN_ENDPOINT_URI,
            Configuration.SSL_TRUSTSTORE_LOCATION,
            Configuration.SSL_TRUSTSTORE_PASSWORD,
            Configuration.SSL_TRUSTSTORE_TYPE
        );
        return jaasConfig;
    }
}
