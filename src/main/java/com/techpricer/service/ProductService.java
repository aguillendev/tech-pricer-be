package com.techpricer.service;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final DolarService dolarService;

    // Regex pattern to capture name and price.
    // Explanation:
    // ^(?:[^\w\s]*) -> Skip leading non-word/non-space chars (like emojis, bullets) - simplified approach
    // actually, let's just capture the name loosely and trim it later.
    // (?<name>.+?) -> Capture name (non-greedy)
    // \s*[-–]*\s* -> Separator (space, dash, en-dash)
    // \$\s* -> Dollar sign and space
    // (?<price>\d+) -> Integer price
    // .*$ -> Any trailing chars
    private static final Pattern PRODUCT_PATTERN = Pattern.compile("^(?:[▪️•\\-\\s]*)(?<name>.+?)\\s*(?:-|–)?\\s*\\$\\s*(?<price>\\d+).*$");

    @Transactional
    public void importProducts(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }

        List<Product> products = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher matcher = PRODUCT_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    String name = matcher.group("name").trim();
                    // Clean up name if it still has leading special chars that weren't caught or if needed
                    // For now, the regex prefix skip (?:[▪️•\-\s]*) should handle common bullets.
                    // But to be safe, we can remove emojis from the start of the name if needed.
                    // Let's assume the regex does a good enough job.

                    int price = Integer.parseInt(matcher.group("price"));

                    Product product = Product.builder()
                            .name(name)
                            .originalPriceUsd(price)
                            .build();
                    products.add(product);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse price in line: {}", line);
                }
            } else {
                log.debug("Line ignored: {}", line);
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
        Double markup = config.getProfitPercentage();
        if (markup == null) markup = 0.0;

        for (Product product : products) {
            if (product.getOriginalPriceUsd() != null) {
                double priceArs = (product.getOriginalPriceUsd() * dolarVenta) * (1 + markup / 100);
                product.setFinalPriceArs(Math.round(priceArs * 100.0) / 100.0); // Round to 2 decimals
            }
        }
        return products;
    }

    @Transactional
    public Product addManualProduct(Product product) {
        return productRepository.save(product);
    }
}
