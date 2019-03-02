package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
@ContextName("camel-kafka-consumer")
public class KafkaConsumer extends RouteBuilder {
	@Override
	public void configure() {
		from("kafka:{{consumer.topic}}"
				+ "?maxPollRecords={{consumer.maxPollRecords}}"
				+ "&consumersCount={{consumer.consumersCount}}"
				+ "&seekTo={{consumer.seekTo}}"
				+ "&groupId={{consumer.group}}")
				.log("${body}");
	}
}
