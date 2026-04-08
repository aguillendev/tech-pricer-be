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

    // Pattern for a single color/variant token: LABEL ($PRICE)
    private static final Pattern VARIANT_TOKEN_PATTERN = Pattern
            .compile("(?<label>[^/()]+?)\\s*\\(\\$\\s*(?<price>[\\d.,]+)\\)");

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
        // Context for the last parsed ▪️ product — used to expand variant sub-lines.
        String lastBaseName = null;   // base product name (without note)
        String lastProductNote = "";  // note suffix already formatted, e.g. " (S/CARG)"
        double lastProductPrice = 0.0;
        int lastProductIndex = -1;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                lastBaseName = null;
                lastProductIndex = -1;
                lastProductNote = "";
                continue;
            }

            // ── Category ──────────────────────────────────────────────────────────────
            Matcher categoryMatcher = CATEGORY_PATTERN.matcher(trimmedLine);
            if (categoryMatcher.find()) {
                currentCategory = categoryMatcher.group(1).trim();
                lastBaseName = null;
                lastProductIndex = -1;
                lastProductNote = "";
                continue;
            }

            // ── Sub-variant line (follows a ▪️ line) ──────────────────────────────────
            if (lastBaseName != null) {

                // Format A: each token has its own price  →  ORANGE ($1400) / BLUE ($1410)
                if (trimmedLine.contains("(") && trimmedLine.contains(")")) {
                    List<Product> variants = new ArrayList<>();
                    String[] segments = trimmedLine.split("\\s*/\\s*");
                    for (String segment : segments) {
                        Matcher vm = VARIANT_TOKEN_PATTERN.matcher(segment.trim());
                        if (vm.find()) {
                            String label = vm.group("label").trim();
                            String priceStr = vm.group("price").replace(",", ".");
                            try {
                                double price = Double.parseDouble(priceStr);
                                variants.add(Product.builder()
                                        .name(lastBaseName + " " + label)
                                        .originalPriceUsd(price)
                                        .category(currentCategory)
                                        .build());
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse variant price in segment '{}'", segment);
                            }
                        }
                    }
                    if (!variants.isEmpty()) {
                        products.remove(lastProductIndex);
                        products.addAll(variants);
                        log.debug("[Format A] Expanded '{}' into {} variants", lastBaseName, variants.size());
                        lastBaseName = null;
                        lastProductIndex = -1;
                        lastProductNote = "";
                        continue;
                    }
                }

                // Format B: plain label(s), no prices  →  "BLUE / GREEN" or just "GRAY"
                // Guard: must not look like a CSV line, contain digits, $ or special chars
                // that would indicate it's something other than a label/colour list.
                boolean looksLikePlainLabel = !trimmedLine.contains("$")
                        && !trimmedLine.contains("(")
                        && !trimmedLine.startsWith("►")
                        && !trimmedLine.startsWith("▪")
                        && !trimmedLine.matches(".*\\d.*")   // no digits → not a price/CSV
                        && !trimmedLine.contains(",");       // no comma → not CSV
                if (looksLikePlainLabel) {
                    String[] labels = trimmedLine.split("\\s*/\\s*");
                    List<Product> variants = new ArrayList<>();
                    for (String label : labels) {
                        String lbl = label.trim();
                        if (!lbl.isEmpty()) {
                            // name = base + label + original note (e.g. " (S/CARG)")
                            variants.add(Product.builder()
                                    .name(lastBaseName + " " + lbl + lastProductNote)
                                    .originalPriceUsd(lastProductPrice)
                                    .category(currentCategory)
                                    .build());
                        }
                    }
                    if (!variants.isEmpty()) {
                        products.remove(lastProductIndex);
                        products.addAll(variants);
                        log.debug("[Format B] Expanded '{}' into {} plain variants", lastBaseName, variants.size());
                        lastBaseName = null;
                        lastProductIndex = -1;
                        lastProductNote = "";
                        continue;
                    }
                }
            }

            // ── Product line (▪️) ───────────────────────────────────────────────────
            Matcher productMatcher = PRODUCT_PATTERN.matcher(trimmedLine);
            if (productMatcher.find()) {
                try {
                    String name = productMatcher.group("name").trim();
                    // Ignoramos texto de cantidad y precio por mayor dentro de parentesis ej: (x 10 uni 22 uss) o (x 8 un 50uss)
                    name = name.replaceAll("(?i)\\s*\\(x\\s*\\d+\\s*un(?:i)?[^)]*\\)", "").trim();
                    String priceStr = productMatcher.group("price").replace(",", ".");
                    double price = Double.parseDouble(priceStr);

                    // Build the note suffix, avoiding double-wrapping parentheses.
                    // Raw note examples: "(S/CARG)", "*S/CARG*", "BLUE / GREEN", "a$ 1410"
                    String rawNote = productMatcher.group("note");
                    String noteAppend = "";  // ready-to-append suffix, e.g. " (S/CARG)"

                    if (rawNote != null && !rawNote.isBlank()) {
                        // Strip: asterisks (formatting), emoji / symbol codepoints, variation selectors
                        String stripped = rawNote.trim()
                                .replaceAll("\\*", "")
                                .replaceAll("[\\p{So}\\p{Cs}\\uFE0F\\u200D]", "")
                                .trim();

                        // Format C: inline variants in the note  →  BLUE / GREEN
                        if (stripped.contains(" / ")) {
                            String[] variantLabels = stripped.split("\\s*/\\s*");
                            for (String vl : variantLabels) {
                                String lbl = vl.trim();
                                if (!lbl.isEmpty()) {
                                    products.add(Product.builder()
                                            .name(name + " " + lbl)
                                            .originalPriceUsd(price)
                                            .category(currentCategory)
                                            .build());
                                }
                            }
                            log.debug("[Format C] Expanded '{}' inline into {} variants", name, variantLabels.length);
                            lastBaseName = null;
                            lastProductIndex = -1;
                            lastProductNote = "";
                            continue;
                        }

                        // Normal note: avoid double-wrapping if already parenthesised
                        if (!stripped.isEmpty()) {
                            if (stripped.startsWith("(") && stripped.endsWith(")")) {
                                noteAppend = " " + stripped;         // e.g. " (S/CARG)"
                            } else {
                                noteAppend = " (" + stripped + ")";  // e.g. " (a$ 1410)"
                            }
                        }
                    }

                    Product product = Product.builder()
                            .name(name + noteAppend)
                            .originalPriceUsd(price)
                            .category(currentCategory)
                            .build();
                    lastProductIndex = products.size();
                    lastBaseName = name;        // clean base name — no note
                    lastProductNote = noteAppend;
                    lastProductPrice = price;
                    products.add(product);
                    continue;
                } catch (NumberFormatException e) {
                    log.warn("Could not parse price in line: {}", line);
                    lastBaseName = null;
                    lastProductIndex = -1;
                    lastProductNote = "";
                }
            } else {
                lastBaseName = null;
                lastProductIndex = -1;
                lastProductNote = "";
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
