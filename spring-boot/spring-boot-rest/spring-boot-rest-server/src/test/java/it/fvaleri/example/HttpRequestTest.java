package it.fvaleri.example;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.apache.camel.test.spring.CamelSpringBootRunner;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;

@RunWith(CamelSpringBootRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HttpRequestTest {
    @MockBean
    private GreetingService greetingService;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void healthShouldReturnOkMessage() throws Exception {
        Assert.assertEquals("{\"status\":\"UP\"}",
                restTemplate.getForObject("http://localhost:" + port + "/actuator/health", String.class));
    }

    @Test
    public void greetShouldReturnFallbackMessage() throws Exception {
        given(greetingService.getGreeting(anyString())).willReturn(new Greeting("Mock"));
        Assert.assertEquals("Mock", restTemplate
                .getForObject("http://localhost:" + port + "/api/greet/fede", Greeting.class).getMessage());
    }
}

