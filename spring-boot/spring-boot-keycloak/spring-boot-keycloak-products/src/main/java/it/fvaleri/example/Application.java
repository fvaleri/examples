package it.fvaleri.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableAutoConfiguration
@EnableFeignClients("it.fvaleri.example.proxies")
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
