package it.fvaleri.example;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application extends RouteBuilder {
    private static final int DELAY_MS = 10_000;

    private static final String[] ENDPOINT_URLS = {
        "http://127.0.0.1:8011/simple",
        "http://127.0.0.1:8012/simple",
        "http://127.0.0.1:8013/simple"
    };

    // track the position in ENDPOINT_URLS
    private int currentURLPos;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configure() throws Exception {
        from("quartz2://foo?trigger.repeatInterval=" + DELAY_MS + "&stateful=true")
            .process((exchange) -> {
                Message message = exchange.getIn();
                message.setHeader(Exchange.DESTINATION_OVERRIDE_URL, ENDPOINT_URLS[currentURLPos]);
                String payload = "Request to " + message.getHeader(Exchange.DESTINATION_OVERRIDE_URL);
                message.setBody(payload);
                currentURLPos = (currentURLPos == ENDPOINT_URLS.length - 1) ? 0 : (currentURLPos + 1);
            })
            .to("cxf:bean:simpleEndpoint?address=foo&defaultOperationName=simple")
            .log("Service invoked: ${body}");
    }
}

