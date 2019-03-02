package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application extends RouteBuilder {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configure() throws Exception {
        from("cxf:bean:simpleEndpoint")
            .log("Processing new request")
            .process((exchange) -> {
                String payload = exchange.getIn().getBody().toString();
                String res = payload + ", response from " + exchange.getIn().getHeader("host");
                exchange.getIn().setBody(res);
            });
    }
}

