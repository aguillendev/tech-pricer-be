package com.techpricer.controller;

import com.techpricer.model.Product;
import com.techpricer.service.DolarService;
import com.techpricer.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final ProductService productService;
    private final DolarService dolarService;

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getProducts() {
        return ResponseEntity.ok(productService.getAllProductsWithCalculatedPrice());
    }

    @GetMapping("/config")
    public ResponseEntity<PublicConfigResponse> getConfig() {
        Double currentDolar = dolarService.getDolarVenta();
        // We can get the update time from the config entity
        LocalDateTime lastUpdated = dolarService.getConfig().getLastUpdated();
        return ResponseEntity.ok(new PublicConfigResponse(currentDolar, lastUpdated));
    }

    public record PublicConfigResponse(Double dolarVenta, LocalDateTime lastUpdated) {}
}
