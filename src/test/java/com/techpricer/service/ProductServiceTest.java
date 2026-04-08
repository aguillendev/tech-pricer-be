package com.techpricer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.repository.GlobalConfigRepository;
import com.techpricer.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private GlobalConfigRepository configRepository;

    @Mock
    private ProfitRuleService profitRuleService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void importProducts_ShouldParseAndSaveValidLines() {
        String input = "► CELULARES\n" +
                "▪️IPHONE 15 128 GB - $ 625.0\n" +
                "► Laptops\n" +
                "▪️MACBOOK AIR M1 - $ 900.5\n" +
                "▪️CARGADOR APPLE 20W USB-C ORIGINAL (x 10 uni 22 uss) - $ 25\n" +
                "▪️TELEVISOR HD 85 (x 8 un 50uss) - $ 2600\n" +
                "▪️OTRO PRODUCTO (x 3 uni 9 uss) - $ 15\n" +
                "Simple Product, 100, Otros"; // CSV Fallback

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(6, savedProducts.size());

        // Check IPHONE
        Product p1 = savedProducts.get(0);
        assertEquals("IPHONE 15 128 GB", p1.getName());
        assertEquals(625.0, p1.getOriginalPriceUsd());
        assertEquals("CELULARES", p1.getCategory());

        // Check MACBOOK
        Product p2 = savedProducts.get(1);
        assertEquals("MACBOOK AIR M1", p2.getName());
        assertEquals(900.5, p2.getOriginalPriceUsd());
        assertEquals("Laptops", p2.getCategory());

        // Check CARGADOR
        Product p3 = savedProducts.get(2);
        assertEquals("CARGADOR APPLE 20W USB-C ORIGINAL", p3.getName());
        assertEquals(25.0, p3.getOriginalPriceUsd());
        assertEquals("Laptops", p3.getCategory());

        // Check TELEVISOR
        Product p4 = savedProducts.get(3);
        assertEquals("TELEVISOR HD 85", p4.getName());
        assertEquals(2600.0, p4.getOriginalPriceUsd());
        assertEquals("Laptops", p4.getCategory());

        // Check OTRO PRODUCTO
        Product p5 = savedProducts.get(4);
        assertEquals("OTRO PRODUCTO", p5.getName());
        assertEquals(15.0, p5.getOriginalPriceUsd());
        assertEquals("Laptops", p5.getCategory());

        // Check Simple Product
        Product p6 = savedProducts.get(5);
        assertEquals("Simple Product", p6.getName());
        assertEquals(100.0, p6.getOriginalPriceUsd());
        assertEquals("Otros", p6.getCategory());
    }

    @Test
    void importProducts_ShouldExpandColorVariants_FormatA() {
        // Format A: sub-line with individual prices per variant
        String input = "► CELULARES\n" +
                "▪️IPHONE 17 PRO MAX 256 GB (ESIM) - $1400 a$ 1410\n" +
                "ORANGE ($1400) / BLUE ($1410)";

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(2, savedProducts.size(), "Should have 2 variant products (no parent)");

        Product orange = savedProducts.get(0);
        assertEquals("IPHONE 17 PRO MAX 256 GB (ESIM) ORANGE", orange.getName());
        assertEquals(1400.0, orange.getOriginalPriceUsd());
        assertEquals("CELULARES", orange.getCategory());

        Product blue = savedProducts.get(1);
        assertEquals("IPHONE 17 PRO MAX 256 GB (ESIM) BLUE", blue.getName());
        assertEquals(1410.0, blue.getOriginalPriceUsd());
        assertEquals("CELULARES", blue.getCategory());
    }

    @Test
    void importProducts_ShouldExpandPlainLabels_FormatB() {
        // Format B: sub-line with plain labels, price and note come from the parent line
        String input = "► CELULARES\n" +
                "▪️MOTOROLA G05 4/256 GB - $ 130 (S/CARG)\n" +
                "BLUE / GREEN";

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(2, savedProducts.size(), "Should have 2 plain-label variants");

        Product blue = savedProducts.get(0);
        assertEquals("MOTOROLA G05 4/256 GB BLUE (S/CARG)", blue.getName());
        assertEquals(130.0, blue.getOriginalPriceUsd());

        Product green = savedProducts.get(1);
        assertEquals("MOTOROLA G05 4/256 GB GREEN (S/CARG)", green.getName());
        assertEquals(130.0, green.getOriginalPriceUsd());
    }

    @Test
    void importProducts_ShouldExpandInlineVariants_FormatC() {
        // Format C: variants listed inline in the same ▪️ line as the note
        String input = "► CELULARES\n" +
                "▪️MOTOROLA G05 4/256 GB - $ 140 BLUE / GREEN";

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(2, savedProducts.size(), "Should have 2 inline variants");

        assertEquals("MOTOROLA G05 4/256 GB BLUE", savedProducts.get(0).getName());
        assertEquals(140.0, savedProducts.get(0).getOriginalPriceUsd());

        assertEquals("MOTOROLA G05 4/256 GB GREEN", savedProducts.get(1).getName());
        assertEquals(140.0, savedProducts.get(1).getOriginalPriceUsd());
    }

    @Test
    void importProducts_ShouldNotDoubleWrapParentheses() {
        // Regression: note "(S/CARG)" must not become "((S/CARG))"
        String input = "► CELULARES\n" +
                "▪️MOTOROLA G05 4/256 GB - $ 130 (S/CARG)";

        productService.importProducts(input);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        Product p = captor.getValue().get(0);
        assertEquals("MOTOROLA G05 4/256 GB (S/CARG)", p.getName());
    }

    @Test
    void importProducts_ShouldExpandSingleLabelSubLine() {
        // Single color on a sub-line with no slash separator
        String input = "► CELULARES\n" +
                "▪️SAMSUNG S25 ULTRA 12/256 GB - $ 945\n" +
                "GRAY";

        productService.importProducts(input);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("SAMSUNG S25 ULTRA 12/256 GB GRAY", saved.get(0).getName());
        assertEquals(945.0, saved.get(0).getOriginalPriceUsd());
    }

    @Test
    void importProducts_ShouldStripEmojiFromNote() {
        // Emoji in the note must be stripped; the color on the sub-line must be appended
        String input = "► CELULARES\n" +
                "▪️SAMSUNG A06 4/64 GB - $ 95 \uD83D\uDD25\n" +
                "BLACK";

        productService.importProducts(input);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> saved = captor.getValue();
        assertEquals(1, saved.size());
        // No emoji in the name, color appended, no extra note
        assertEquals("SAMSUNG A06 4/64 GB BLACK", saved.get(0).getName());
        assertEquals(95.0, saved.get(0).getOriginalPriceUsd());
    }

    @Test
    void getAllProductsWithCalculatedPrice_ShouldCalculateCorrectly() {
        Product p = Product.builder().name("Test").originalPriceUsd(100.0).build();
        when(productRepository.findAll()).thenReturn(List.of(p));

        GlobalConfig config = GlobalConfig.builder().profitPercentage(20.0).build();
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));

        // Sin reglas → usa el margen global (20.0)
        when(profitRuleService.getAllRules()).thenReturn(Collections.emptyList());
        when(profitRuleService.resolveProfit(any(), any())).thenReturn(null);

        // El dólar ahora se pasa como parámetro (1000.0)
        List<Product> result = productService.getAllProductsWithCalculatedPrice(1000.0);

        assertEquals(1, result.size());
        // 100 * 1000 * 1.20 = 120000
        assertEquals(120000.0, result.get(0).getFinalPriceArs());
    }
}
