package it.fvaleri.example;

import io.quarkus.runtime.Startup;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Startup
@ApplicationScoped
public class ArtemisBroker {
    private static final Logger LOG = LoggerFactory.getLogger(ArtemisBroker.class);
    private ActiveMQServer server;

    @PostConstruct
    void init() throws Exception {
        LOG.info("Starting Artemis embedded broker");
        SecurityConfiguration securityConfig = new SecurityConfiguration();
        securityConfig.addUser("admin", "changeit");
        securityConfig.addRole("admin", "amq");
        securityConfig.setDefaultUser("admin");
        ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfig);
        server = ActiveMQServers.newActiveMQServer("broker.xml", null, securityManager);
        server.start();
    }

    @PreDestroy
    void destroy() {
        LOG.info("Stopping Artemis embedded broker");
        try {
            server.stop();
        } catch (Exception e) {
            LOG.error("{}", e);
        }
    }
}
