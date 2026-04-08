package com.techpricer.service;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.model.ProfitRule;
import com.techpricer.repository.GlobalConfigRepository;
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
    private final GlobalConfigRepository configRepository;
    private final ProfitRuleService profitRuleService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private GlobalConfig getConfig() {
        return configRepository.findById(1L).orElseGet(() -> {
            GlobalConfig c = GlobalConfig.builder().id(1L).profitPercentage(0.0).build();
            return configRepository.save(c);
        });
    }

    // Pattern for Category: Starts with ►
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^►\\s*(.*)");

    // Pattern for Product: Starts with ▪️, Name, - $ Price, optional note after
    // price (e.g. *S/CARG*)
    private static final Pattern PRODUCT_PATTERN = Pattern
            .compile("^▪️\\s*(?<name>.+?)\\s*-\\s*\\$\\s*(?<price>[\\d.,]+)(?:\\s+(?<note>.+))?$");

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
                    // Ignoramos texto de cantidad y precio por mayor dentro de parentesis ej: (x 10 uni 22 uss) o (x 8 un 50uss)
                    name = name.replaceAll("(?i)\\s*\\(x\\s*\\d+\\s*un(?:i)?[^)]*\\)", "").trim();
                    String priceStr = productMatcher.group("price").replace(",", ".");
                    Double price = Double.parseDouble(priceStr);

                    // Append optional note (e.g. *S/CARG* -> S/CARG) to the product name
                    String rawNote = productMatcher.group("note");
                    if (rawNote != null && !rawNote.isBlank()) {
                        String cleanNote = rawNote.trim().replaceAll("\\*", "").trim();
                        if (!cleanNote.isEmpty()) {
                            name = name + " (" + cleanNote + ")";
                        }
                    }

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

    /**
     * @param dolarVenta cotización obtenida previamente por el controller (nunca
     *                   null).
     */
    public List<Product> getAllProductsWithCalculatedPrice(Double dolarVenta) {
        List<Product> products = productRepository.findAll();
        GlobalConfig config = getConfig();
        Double globalMarkup = config.getProfitPercentage() != null ? config.getProfitPercentage() : 0.0;

        List<ProfitRule> rules = profitRuleService.getAllRules();
        log.debug("[PriceCalc] Rules loaded: {}, globalMarkup: {}, dolar: {}", rules.size(), globalMarkup, dolarVenta);

        for (Product product : products) {
            if (product.getOriginalPriceUsd() != null) {
                Double resolvedMarkup = profitRuleService.resolveProfit(product.getOriginalPriceUsd(), rules);
                double markup = resolvedMarkup != null ? resolvedMarkup : globalMarkup;
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

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional
    public void deleteProducts(List<Long> ids) {
        productRepository.deleteAllById(ids);
    }

    /**
     * Calcula el finalPriceArs de un producto individual.
     * 
     * @param dolarVenta cotización obtenida previamente por el controller.
     */
    public Product calculatePriceForProduct(Product product, Double dolarVenta) {
        if (product.getOriginalPriceUsd() == null) {
            return product;
        }
        GlobalConfig config = getConfig();
        Double globalMarkup = config.getProfitPercentage() != null ? config.getProfitPercentage() : 0.0;

        List<ProfitRule> rules = profitRuleService.getAllRules();
        Double resolvedMarkup = profitRuleService.resolveProfit(product.getOriginalPriceUsd(), rules);
        double markup = resolvedMarkup != null ? resolvedMarkup : globalMarkup;

        double priceArs = (product.getOriginalPriceUsd() * dolarVenta) * (1 + markup / 100);
        product.setFinalPriceArs(Math.round(priceArs * 100.0) / 100.0);
        log.debug("[PriceCalc] '{}' usd={} markup={}% dolar={} -> finalArs={}",
                product.getName(), product.getOriginalPriceUsd(), markup, dolarVenta, product.getFinalPriceArs());
        return product;
    }
}
