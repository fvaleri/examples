package it.fvaleri.example;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Greeter {
    public String greet() {
        String name = "unknown-host";
        try {
            name = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
        }
        return "Hello " + name;
    }
}
