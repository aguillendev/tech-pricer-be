package com.techpricer.controller;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.repository.GlobalConfigRepository;
import com.techpricer.service.DolarService;
import com.techpricer.service.DolarService.DollarRateUnavailableException;
import com.techpricer.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final ProductService productService;
    private final DolarService dolarService;
    private final GlobalConfigRepository configRepository;

    /**
     * Devuelve todos los productos con su precio en ARS calculado en tiempo real.
     * Si no se puede obtener la cotización del dólar retorna HTTP 503.
     */
    @GetMapping("/products")
    public ResponseEntity<?> getProducts() {
        try {
            Double dolarVenta = dolarService.getDolarVenta();
            List<Product> products = productService.getAllProductsWithCalculatedPrice(dolarVenta);
            return ResponseEntity.ok(products);
        } catch (DollarRateUnavailableException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Devuelve la configuración pública (margen de ganancia + cotización actual).
     * Si no se puede obtener la cotización del dólar retorna HTTP 503.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        try {
            Double dolarVenta = dolarService.getDolarVenta();
            GlobalConfig config = configRepository.findById(1L)
                    .orElseGet(() -> GlobalConfig.builder().id(1L).profitPercentage(0.0).build());
            Double profitMargin = config.getProfitPercentage();
            return ResponseEntity.ok(new PublicConfigResponse(
                    dolarVenta,
                    profitMargin != null ? profitMargin : 0.0));
        } catch (DollarRateUnavailableException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    public record PublicConfigResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("dollarRate") Double dollarRate,
            @com.fasterxml.jackson.annotation.JsonProperty("profitMargin") Double profitMargin) {
    }

    public record ErrorResponse(String error) {
    }
}
