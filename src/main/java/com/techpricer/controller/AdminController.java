package com.techpricer.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.model.ProfitRule;
import com.techpricer.repository.GlobalConfigRepository;
import com.techpricer.service.DolarService;
import com.techpricer.service.ProductService;
import com.techpricer.service.ProfitRuleService;
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
    private final ProfitRuleService profitRuleService;

    @PostMapping("/import")
    public ResponseEntity<ErrorMessageResponse> importProducts(@RequestBody String rawText) {
        productService.importProducts(rawText);
        // Devolvemos los productos con precios ya calculados (applica reglas de
        // ganancia)
        java.util.List<Product> calculatedProducts = productService.getAllProductsWithCalculatedPrice();
        return ResponseEntity.ok(new ErrorMessageResponse(true, "Products imported successfully", calculatedProducts));
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
        // Calcular precio final con reglas de ganancia
        Product withPrice = productService.calculatePriceForProduct(saved);
        return ResponseEntity.ok(withPrice);
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
            @JsonProperty("profitMargin") Double profitMargin,
            Double manualDollarValue) {
    }

    public record ErrorMessageResponse(boolean success, String message, java.util.List<Product> products) {
    }
}
