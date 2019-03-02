package it.fvaleri.example;

import org.apache.camel.component.jms.JmsComponent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.jms.ConnectionFactory;

@SpringBootApplication
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean(name = "jms")
	public JmsComponent jmsComponent(PlatformTransactionManager jtaTransactionManager, ConnectionFactory connectionFactory) {
		JmsComponent component = new JmsComponent();
		component.setTransactionManager(jtaTransactionManager);
		component.setConnectionFactory(connectionFactory);
		component.setTransacted(false); // whether to use local TXs
		component.setMaxConcurrentConsumers(1); // set one per JMS route
		component.setCacheLevelName("CACHE_NONE"); // no cache with XA
		component.setDeliveryPersistent(true);
		component.setRequestTimeout(10000);
		return component;
	}
}
