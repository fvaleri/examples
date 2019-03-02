package it.fvaleri.example;

import javax.jws.WebMethod;
import javax.jws.WebService;

@WebService(targetNamespace = "http://it.fvaleri.example/hello")
public interface HelloService {
    @WebMethod
    String writeText(String text);
}
