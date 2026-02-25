package com.techpricer.service;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.model.ProfitRule;
import com.techpricer.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final DolarService dolarService;
    private final ProfitRuleService profitRuleService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // Pattern for Category: Starts with ►
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^►\\s*(.*)");

    // Pattern for Product: Starts with ▪️, Name, - $ Price
    private static final Pattern PRODUCT_PATTERN = Pattern
            .compile("^▪️\\s*(?<name>.+?)\\s*-\\s*\\$\\s*(?<price>[\\d.,]+).*$");

    @Transactional
    public void importProducts(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }

        // Handle JSON wrapper if present ({"data": "..."})
        if (rawText.trim().startsWith("{")) {
            try {
                var jsonNode = objectMapper.readTree(rawText);
                if (jsonNode.has("data")) {
                    rawText = jsonNode.get("data").asText();
                }
            } catch (java.io.IOException e) {
                log.warn("Failed to parse rawText as JSON, treating as plain text", e);
            }
        }

        List<Product> products = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");
        String currentCategory = "";

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty())
                continue;

            // Check for Category
            Matcher categoryMatcher = CATEGORY_PATTERN.matcher(trimmedLine);
            if (categoryMatcher.find()) {
                currentCategory = categoryMatcher.group(1).trim();
                continue;
            }

            // Check for Product
            Matcher productMatcher = PRODUCT_PATTERN.matcher(trimmedLine);
            if (productMatcher.find()) {
                try {
                    String name = productMatcher.group("name").trim();
                    String priceStr = productMatcher.group("price").replace(",", ".");
                    Double price = Double.parseDouble(priceStr);

                    Product product = Product.builder()
                            .name(name)
                            .originalPriceUsd(price)
                            .category(currentCategory)
                            .build();
                    products.add(product);
                    continue;
                } catch (NumberFormatException e) {
                    log.warn("Could not parse price in line: {}", line);
                }
            }

            // Fallback: CSV (Name, Price, Category)
            if (trimmedLine.contains(",") && !trimmedLine.startsWith("►") && !trimmedLine.startsWith("▪️")) {
                String[] parts = trimmedLine.split(",");
                if (parts.length >= 2) {
                    try {
                        String name = parts[0].trim();
                        String pricePart = parts[1].trim().replace("$", "");
                        Double price = Double.parseDouble(pricePart);
                        String category = parts.length > 2 ? parts[2].trim() : currentCategory;

                        Product product = Product.builder()
                                .name(name)
                                .originalPriceUsd(price)
                                .category(category)
                                .build();
                        products.add(product);
                    } catch (Exception e) {
                        log.debug("Line failed CSV parsing: {}", line);
                    }
                }
            }
        }

        if (!products.isEmpty()) {
            productRepository.deleteAll();
            productRepository.saveAll(products);
            log.info("Imported {} products", products.size());
        }
    }

    public List<Product> getAllProductsWithCalculatedPrice() {
        List<Product> products = productRepository.findAll();
        GlobalConfig config = dolarService.getConfig();
        Double dolarVenta = dolarService.getDolarVenta();
        Double globalMarkup = config.getProfitPercentage() != null ? config.getProfitPercentage() : 0.0;

        List<ProfitRule> rules = profitRuleService.getAllRules();
        log.debug("[PriceCalc] Rules loaded: {}, globalMarkup: {}", rules.size(), globalMarkup);
        rules.forEach(r -> log.debug("  Rule id={} min={} max={} profit={}",
                r.getId(), r.getMinPriceUsd(), r.getMaxPriceUsd(), r.getProfitPercentage()));

        for (Product product : products) {
            if (product.getOriginalPriceUsd() != null) {
                Double resolvedMarkup = profitRuleService.resolveProfit(product.getOriginalPriceUsd(), rules);
                double markup = resolvedMarkup != null ? resolvedMarkup : globalMarkup;
                log.debug("  Product '{}' usd={} -> resolvedMarkup={} usedMarkup={}",
                        product.getName(), product.getOriginalPriceUsd(), resolvedMarkup, markup);

                double priceArs = (product.getOriginalPriceUsd() * dolarVenta) * (1 + markup / 100);
                product.setFinalPriceArs(Math.round(priceArs * 100.0) / 100.0);
            }
        }

        return products;
    }

    @Transactional
    public Product addManualProduct(Product product) {
        return productRepository.save(product);
    }

    /**
     * Calcula el finalPriceArs de un producto individual aplicando las reglas de
     * ganancia
     * por tramos. Si ninguna regla aplica, usa el porcentaje de ganancia global.
     * No persiste el cambio: solo actualiza el objeto en memoria.
     */
    public Product calculatePriceForProduct(Product product) {
        if (product.getOriginalPriceUsd() == null) {
            return product;
        }
        GlobalConfig config = dolarService.getConfig();
        Double dolarVenta = dolarService.getDolarVenta();
        Double globalMarkup = config.getProfitPercentage() != null ? config.getProfitPercentage() : 0.0;

        List<ProfitRule> rules = profitRuleService.getAllRules();
        Double resolvedMarkup = profitRuleService.resolveProfit(product.getOriginalPriceUsd(), rules);
        double markup = resolvedMarkup != null ? resolvedMarkup : globalMarkup;

        double priceArs = (product.getOriginalPriceUsd() * dolarVenta) * (1 + markup / 100);
        product.setFinalPriceArs(Math.round(priceArs * 100.0) / 100.0);
        log.debug("[PriceCalc] Producto '{}' usd={} markup={}% -> finalArs={}",
                product.getName(), product.getOriginalPriceUsd(), markup, product.getFinalPriceArs());
        return product;
    }
}
