package it.fvaleri.example;

import me.snowdrop.boot.narayana.autoconfigure.NarayanaConfiguration;
import org.apache.camel.component.jms.springboot.JmsComponentAutoConfiguration;
import org.apache.camel.component.jpa.springboot.JpaComponentAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

@SpringBootConfiguration
@ComponentScan
@EnableAutoConfiguration(exclude = {
		NarayanaConfiguration.class,
		JtaAutoConfiguration.class,
		DataSourceAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		JpaComponentAutoConfiguration.class,
		ArtemisAutoConfiguration.class,
		JmsComponentAutoConfiguration.class
})
@ImportResource("classpath:spring-camel.xml")
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
