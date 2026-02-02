package com.techpricer.controller;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.repository.GlobalConfigRepository;
import com.techpricer.service.DolarService;
import com.techpricer.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final GlobalConfigRepository configRepository;
    private final DolarService dolarService;

    @PostMapping("/import")
    public ResponseEntity<String> importProducts(@RequestBody String rawText) {
        productService.importProducts(rawText);
        return ResponseEntity.ok("Products imported successfully");
    }

    @PutMapping("/config")
    public ResponseEntity<GlobalConfig> updateConfig(@RequestBody ConfigUpdateRequest request) {
        GlobalConfig config = dolarService.getConfig();
        if (request.profitPercentage() != null) {
            config.setProfitPercentage(request.profitPercentage());
        }
        if (request.manualDollarValue() != null) {
            config.setManualDollarValue(request.manualDollarValue());
        }
        configRepository.save(config);
        return ResponseEntity.ok(config);
    }

    @PostMapping("/products")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        Product saved = productService.addManualProduct(product);
        return ResponseEntity.ok(saved);
    }

    public record ConfigUpdateRequest(Double profitPercentage, Double manualDollarValue) {}
}
