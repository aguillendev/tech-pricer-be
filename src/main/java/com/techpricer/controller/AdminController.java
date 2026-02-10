package com.techpricer.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@CrossOrigin(origins = { "http://localhost:5173", "https://tech-pricer-bo.vercel.app" }) // Allow frontend
public class AdminController {

    private final ProductService productService;
    private final GlobalConfigRepository configRepository;
    private final DolarService dolarService;

    @PostMapping("/import")
    public ResponseEntity<ErrorMessageResponse> importProducts(@RequestBody String rawText) {
        productService.importProducts(rawText);
        return ResponseEntity.ok(new ErrorMessageResponse(true, "Products imported successfully", null));
    }

    @PostMapping("/config")
    public ResponseEntity<GlobalConfig> updateConfig(@RequestBody ConfigUpdateRequest request) {
        GlobalConfig config = dolarService.getConfig();
        if (request.profitMargin() != null) {
            config.setProfitPercentage(request.profitMargin());
        }
        if (request.manualDollarValue() != null) {
            config.setManualDollarValue(request.manualDollarValue());
        }
        configRepository.save(config);
        return ResponseEntity.ok(config);
    }

    @PostMapping("/product") // Align with frontend: /product (singular)
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        Product saved = productService.addManualProduct(product);
        return ResponseEntity.ok(saved);
    }

    public record ConfigUpdateRequest(
            @JsonProperty("profitMargin") Double profitMargin,
            Double manualDollarValue) {
    }

    public record ErrorMessageResponse(boolean success, String message, java.util.List<Product> products) {
    }
}
