package it.fvaleri.example.controllers;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.fvaleri.example.models.Product;
import it.fvaleri.example.models.Provider;
import it.fvaleri.example.proxies.ProviderProxy;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProviderProxy providerProxy;

	private List<Product> products = Stream.of(
			new Product("1", "product1", "1"),
			new Product("2", "product2", "1"),
			new Product("3", "product3", "2")
		)
		.collect(Collectors.toList());

	@GetMapping
	public List<Product> getAll() {
		return products;
	}

	@GetMapping("/{id}")
	public ResponseEntity<Product> getDetails(@PathVariable String id) {
		Optional<Product> product$ = products.stream().filter(p -> id.equals(p.getId())).findFirst();
		if (!product$.isPresent()) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
        Product product = product$.get();
        Provider provider = providerProxy.getDetails(product.getProviderId());
        product.setProvider(provider);
        return new ResponseEntity<Product>(product, HttpStatus.ACCEPTED);
	}

}
