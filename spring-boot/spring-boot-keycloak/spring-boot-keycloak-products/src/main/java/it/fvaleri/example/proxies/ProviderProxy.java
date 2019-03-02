package it.fvaleri.example.proxies;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import it.fvaleri.example.config.ClientConfiguration;
import it.fvaleri.example.models.Provider;

@FeignClient(name = "providers-service", url = "${microservice.providers.url}", configuration = {ClientConfiguration.class})
public interface ProviderProxy {
    @GetMapping("/providers/{id}")
    public Provider getDetails(@PathVariable("id") String id);

}
