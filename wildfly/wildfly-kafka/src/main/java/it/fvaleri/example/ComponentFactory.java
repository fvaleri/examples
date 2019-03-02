package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.properties.PropertiesComponent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

@ApplicationScoped
public class ComponentFactory {
	@Inject
	@ContextName("camel-kafka-consumer")
	private CamelContext camelContext;

	@Produces
	@Named("kafka")
	public KafkaComponent createKafkaComponent() throws Exception {
		KafkaComponent kafkaComponent = new KafkaComponent();
		KafkaConfiguration kafkaConfiguration = new KafkaConfiguration();
		kafkaConfiguration.setBrokers(camelContext.resolvePropertyPlaceholders("{{kafka.bootstrapUrl}}"));
		String securityProtocol = camelContext.resolvePropertyPlaceholders("{{kafka.securityProtocol}}");
		if (securityProtocol != null && securityProtocol.equals("SASL_SSL")) {
			kafkaConfiguration.setSecurityProtocol(securityProtocol);
			kafkaConfiguration.setSaslMechanism(camelContext.resolvePropertyPlaceholders("{{kafka.saslMechanism}}"));
			kafkaConfiguration.setSaslJaasConfig(camelContext.resolvePropertyPlaceholders("{{kafka.saslJaasConfig}}"));
			kafkaConfiguration.setSslEnabledProtocols("TLSv1.2,TLSv1.3");
			kafkaConfiguration.setSslProtocol("TLSv1.3");
		}
		kafkaComponent.setConfiguration(kafkaConfiguration);
		return kafkaComponent;
	}

	@Produces
	@Named("properties")
	public PropertiesComponent createPropertiesComponent() {
		PropertiesComponent properties = new PropertiesComponent();
		properties.setLocation("classpath:application.properties");
		return properties;
	}
}
