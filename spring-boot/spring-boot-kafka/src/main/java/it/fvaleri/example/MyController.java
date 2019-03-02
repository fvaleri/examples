package it.fvaleri.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class MyController {
    MyProducer producer;

    public MyController(MyProducer producer) {
        this.producer = producer;
    }

    @GetMapping
    public void sendTransaction(@RequestParam(value="error", required = false, defaultValue = "false") boolean error)
            throws InterruptedException {
        producer.send(error);
    }
}
