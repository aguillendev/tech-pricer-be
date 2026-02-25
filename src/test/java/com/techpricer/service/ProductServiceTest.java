package com.techpricer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DolarService dolarService;

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
                "Simple Product, 100, Otros"; // CSV Fallback

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(3, savedProducts.size());

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

        // Check Simple Product
        Product p3 = savedProducts.get(2);
        assertEquals("Simple Product", p3.getName());
        assertEquals(100.0, p3.getOriginalPriceUsd());
        assertEquals("Otros", p3.getCategory());
    }

    @Test
    void getAllProductsWithCalculatedPrice_ShouldCalculateCorrectly() {
        Product p = Product.builder().name("Test").originalPriceUsd(100.0).build();
        when(productRepository.findAll()).thenReturn(List.of(p));

        GlobalConfig config = GlobalConfig.builder().profitPercentage(20.0).build();
        when(dolarService.getConfig()).thenReturn(config);
        when(dolarService.getDolarVenta()).thenReturn(1000.0);

        // Sin reglas → usa el margen global (20.0)
        when(profitRuleService.getAllRules()).thenReturn(Collections.emptyList());
        when(profitRuleService.resolveProfit(any(), any())).thenReturn(null);

        List<Product> result = productService.getAllProductsWithCalculatedPrice();

        assertEquals(1, result.size());
        Product resP = result.get(0);
        // 100 * 1000 * 1.20 = 120000
        assertEquals(120000.0, resP.getFinalPriceArs());
    }
}
