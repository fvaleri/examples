package it.fvaleri.example;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Named;

import javax.jms.ConnectionFactory;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.quarkus.core.events.ComponentAddEvent;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

@ApplicationScoped
public class JmsConfiguration {
	@ConfigProperty(name = "jms.url")
	public String url;

	@ConfigProperty(name = "jms.username")
	public String username;

	@ConfigProperty(name = "jms.password")
	public String password;

	@ConfigProperty(name = "jms.totalMessages")
	public int totalMessages;

	@ConfigProperty(name = "jms.maxConcurrentProducers")
	public int maxConcurrentProducers;

	@ConfigProperty(name = "jms.maxConcurrentConsumers")
	public int maxConcurrentConsumers;

	@ConfigProperty(name = "jms.enableConnectionPooling")
	public boolean enableConnectionPooling;

	@ConfigProperty(name = "jms.cacheLevelName")
	public String cacheLevelName;

	@ConfigProperty(name = "jms.cacheSize")
	public long cacheSize;

	@ConfigProperty(name = "jms.keystore.path")
	public String keyStorePath;

	@ConfigProperty(name = "jms.keystore.password")
	public String keyStorePassword;

	@ConfigProperty(name = "jms.truststore.path")
	public String trustStorePath;

	@ConfigProperty(name = "jms.truststore.password")
	public String trustStorePassword;

	// producer method (skip Quarkus optimization)
	@Named("jms")
	public AMQPComponent camelComponent() {
		// AMQP component uses JMS component which uses Spring DMLC
		AMQPComponent component = new AMQPComponent();
		component.setConnectionFactory(enableConnectionPooling ? pooledConnectionFactory() : connectionFactory());
		component.setTransacted(false);
		component.setMaxConcurrentConsumers(maxConcurrentConsumers);
		component.setCacheLevelName(cacheLevelName);
		component.setDeliveryPersistent(true);
		component.setRequestTimeout(10000);
		return component;
	}

	// consumer method (do not skip Quarkus optimizations)
	/*public void onComponentAdd(@Observes ComponentAddEvent event) {
		if (event.getComponent() instanceof AMQPComponent) {
			AMQPComponent component = ((AMQPComponent) event.getComponent());
			component.setConnectionFactory(enableConnectionPooling ? pooledConnectionFactory() : connectionFactory());
			component.setTransacted(false);
			component.setMaxConcurrentConsumers(maxConcurrentConsumers);
			component.setCacheLevelName(cacheLevelName);
			component.setDeliveryPersistent(true);
			component.setRequestTimeout(10000);
		}
	}*/

	public ConnectionFactory connectionFactory() {
		String connectionUrl = String.format(
				"%s?jms.prefetchPolicy.all=%d&transport.keyStoreLocation=%s&transport.keyStorePassword=%s" +
						"&transport.trustStoreLocation=%s&transport.trustStorePassword=%s&transport.verifyHost=false",
				url, cacheSize, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword);
		return new JmsConnectionFactory(username, password, connectionUrl);
	}

	public ConnectionFactory pooledConnectionFactory() {
		// manually creating a pooled CF as it is not yet available in Quarkus
		JmsPoolConnectionFactory jmsPoolConnectionFactory = new JmsPoolConnectionFactory();
		jmsPoolConnectionFactory.setConnectionFactory(connectionFactory());
		// we only need one connection and we want to keep it opened
		jmsPoolConnectionFactory.setMaxConnections(1);
		jmsPoolConnectionFactory.setConnectionIdleTimeout(0);
		jmsPoolConnectionFactory.setUseAnonymousProducers(false);
		return jmsPoolConnectionFactory;
	}
}
