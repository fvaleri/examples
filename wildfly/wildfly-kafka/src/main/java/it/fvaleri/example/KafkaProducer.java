package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
@ContextName("camel-kafka-producer")
public class KafkaProducer extends RouteBuilder {
	@Override
	public void configure() {
		from("timer://foo?fixedRate=true&period=5000")
			.setBody(simple("Greetings at fixed rate"))
			.to("kafka:{{producer.topic}}");
	}
}
