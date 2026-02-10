package com.techpricer.controller;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.service.DolarService;
import com.techpricer.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://localhost:5173", "https://tech-pricer-bo.vercel.app" })
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
        GlobalConfig config = dolarService.getConfig();
        // We can get the update time from the config entity
        LocalDateTime lastUpdated = config.getLastUpdated();
        Double profitMargin = config.getProfitPercentage();

        return ResponseEntity.ok(new PublicConfigResponse(
                currentDolar,
                profitMargin != null ? profitMargin : 0.0,
                lastUpdated));
    }

    // Match frontend expected mapping
    public record PublicConfigResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("dollarRate") Double dollarRate,
            @com.fasterxml.jackson.annotation.JsonProperty("profitMargin") Double profitMargin,
            LocalDateTime lastUpdated) {
    }
}
