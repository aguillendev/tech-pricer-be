package com.techpricer.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.model.ProfitRule;
import com.techpricer.repository.GlobalConfigRepository;
import com.techpricer.service.DolarService;
import com.techpricer.service.DolarService.DollarRateUnavailableException;
import com.techpricer.service.ProductService;
import com.techpricer.service.ProfitRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final GlobalConfigRepository configRepository;
    private final DolarService dolarService;
    private final ProfitRuleService profitRuleService;

    private GlobalConfig getConfig() {
        return configRepository.findById(1L).orElseGet(() -> {
            GlobalConfig c = GlobalConfig.builder().id(1L).profitPercentage(0.0).build();
            return configRepository.save(c);
        });
    }

    @PostMapping("/import")
    public ResponseEntity<?> importProducts(@RequestBody String rawText) {
        try {
            Double dolarVenta = dolarService.getDolarVenta();
            productService.importProducts(rawText);
            java.util.List<Product> calculatedProducts = productService.getAllProductsWithCalculatedPrice(dolarVenta);
            return ResponseEntity
                    .ok(new ErrorMessageResponse(true, "Products imported successfully", calculatedProducts));
        } catch (DollarRateUnavailableException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorMessageResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/config")
    public ResponseEntity<GlobalConfig> updateConfig(@RequestBody ConfigUpdateRequest request) {
        GlobalConfig config = getConfig();
        if (request.profitMargin() != null) {
            config.setProfitPercentage(request.profitMargin());
        }
        configRepository.save(config);
        return ResponseEntity.ok(config);
    }

    @PostMapping("/product")
    public ResponseEntity<?> addProduct(@RequestBody Product product) {
        try {
            Double dolarVenta = dolarService.getDolarVenta();
            Product saved = productService.addManualProduct(product);
            Product withPrice = productService.calculatePriceForProduct(saved, dolarVenta);
            return ResponseEntity.ok(withPrice);
        } catch (DollarRateUnavailableException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/products")
    public ResponseEntity<Void> deleteProducts(@RequestBody java.util.List<Long> ids) {
        productService.deleteProducts(ids);
        return ResponseEntity.noContent().build();
    }

    // ── Reglas de ganancia ──────────────────────────────────────────────────────

    @GetMapping("/rules")
    public ResponseEntity<java.util.List<ProfitRule>> getRules() {
        return ResponseEntity.ok(profitRuleService.getAllRules());
    }

    @PostMapping("/rules")
    public ResponseEntity<ProfitRule> createRule(@RequestBody ProfitRule rule) {
        return ResponseEntity.ok(profitRuleService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<ProfitRule> updateRule(@PathVariable Long id, @RequestBody ProfitRule rule) {
        return ResponseEntity.ok(profitRuleService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        profitRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    public record ConfigUpdateRequest(
            @JsonProperty("profitMargin") Double profitMargin) {
    }

    public record ErrorMessageResponse(boolean success, String message, java.util.List<Product> products) {
    }
}
