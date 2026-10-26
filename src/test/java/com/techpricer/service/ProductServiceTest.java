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
                "▪️CASO 1 - $2.700\n" +
                "▪️CASO 2 - $ 2.700\n" +
                "▪️CASO 3 - $ 2700\n" +
                "▪️CASO 4 - $2700\n" +
                "▪️CASO 5 - $2700.00\n" +
                "▪️CASO 6 - $ 2.700.00\n" +
                "► Laptops\n" +
                "▪️MACBOOK AIR M1 - $ 900.5\n" +
                "▪️CARGADOR APPLE 20W USB-C ORIGINAL (x 10 uni 22 uss) - $ 25\n" +
                "▪️TELEVISOR HD 85 (x 8 un 50uss) - $ 2600\n" +
                "▪️OTRO PRODUCTO (x 3 uni 9 uss) - $ 15\n" +
                "▪️MACBOOK PRO 16 - $ 2.700,50\n" +
                "Simple Product, 100, Otros\n" +
                "CSV Thousand Product, $2.700, Otros"; // CSV Fallback

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(14, savedProducts.size());

        // Check IPHONE
        Product p1 = savedProducts.get(0);
        assertEquals("IPHONE 15 128 GB", p1.getName());
        assertEquals(625.0, p1.getOriginalPriceUsd());

        // Check CASO 1: $2.700
        assertEquals("CASO 1", savedProducts.get(1).getName());
        assertEquals(2700.0, savedProducts.get(1).getOriginalPriceUsd());

        // Check CASO 2: $ 2.700
        assertEquals("CASO 2", savedProducts.get(2).getName());
        assertEquals(2700.0, savedProducts.get(2).getOriginalPriceUsd());

        // Check CASO 3: $ 2700
        assertEquals("CASO 3", savedProducts.get(3).getName());
        assertEquals(2700.0, savedProducts.get(3).getOriginalPriceUsd());

        // Check CASO 4: $2700
        assertEquals("CASO 4", savedProducts.get(4).getName());
        assertEquals(2700.0, savedProducts.get(4).getOriginalPriceUsd());

        // Check CASO 5: $2700.00
        assertEquals("CASO 5", savedProducts.get(5).getName());
        assertEquals(2700.0, savedProducts.get(5).getOriginalPriceUsd());

        // Check CASO 6: $ 2.700.00
        assertEquals("CASO 6", savedProducts.get(6).getName());
        assertEquals(2700.0, savedProducts.get(6).getOriginalPriceUsd());

        // Check MACBOOK
        assertEquals("MACBOOK AIR M1", savedProducts.get(7).getName());
        assertEquals(900.5, savedProducts.get(7).getOriginalPriceUsd());

        // Check CARGADOR
        assertEquals("CARGADOR APPLE 20W USB-C ORIGINAL", savedProducts.get(8).getName());
        assertEquals(25.0, savedProducts.get(8).getOriginalPriceUsd());

        // Check TELEVISOR
        assertEquals("TELEVISOR HD 85", savedProducts.get(9).getName());
        assertEquals(2600.0, savedProducts.get(9).getOriginalPriceUsd());

        // Check OTRO PRODUCTO
        assertEquals("OTRO PRODUCTO", savedProducts.get(10).getName());
        assertEquals(15.0, savedProducts.get(10).getOriginalPriceUsd());

        // Check MACBOOK PRO 16 ($ 2.700,50)
        assertEquals("MACBOOK PRO 16", savedProducts.get(11).getName());
        assertEquals(2700.5, savedProducts.get(11).getOriginalPriceUsd());

        // Check Simple Product
        assertEquals("Simple Product", savedProducts.get(12).getName());
        assertEquals(100.0, savedProducts.get(12).getOriginalPriceUsd());

        // Check CSV Thousand Product
        assertEquals("CSV Thousand Product", savedProducts.get(13).getName());
        assertEquals(2700.0, savedProducts.get(13).getOriginalPriceUsd());
    }

    @Test
    void parsePrice_ShouldSupportThousandSeparatorsAndDecimals() {
        // Los 6 casos consultados por el usuario:
        assertEquals(2700.0, ProductService.parsePrice("$2.700"));
        assertEquals(2700.0, ProductService.parsePrice("$ 2.700"));
        assertEquals(2700.0, ProductService.parsePrice("$ 2700"));
        assertEquals(2700.0, ProductService.parsePrice("$2700"));
        assertEquals(2700.0, ProductService.parsePrice("$2700.00"));
        assertEquals(2700.0, ProductService.parsePrice("$ 2.700.00"));

        // Otros casos de miles con punto
        assertEquals(2700.0, ProductService.parsePrice("2.700"));
        assertEquals(2700.0, ProductService.parsePrice("2.700,00"));
        assertEquals(2700.5, ProductService.parsePrice("2.700,50"));
        assertEquals(2700.5, ProductService.parsePrice("2.700,5"));
        assertEquals(1250000.0, ProductService.parsePrice("1.250.000"));
        assertEquals(1250000.75, ProductService.parsePrice("1.250.000,75"));

        // Decimales con punto (1 o 2 dígitos tras el punto)
        assertEquals(625.0, ProductService.parsePrice("625.0"));
        assertEquals(900.5, ProductService.parsePrice("900.5"));
        assertEquals(15.50, ProductService.parsePrice("15.50"));
        assertEquals(25.99, ProductService.parsePrice("$25.99"));

        // Coma exclusivamente como separador decimal:
        assertEquals(2700.5, ProductService.parsePrice("2.700,50"));
        assertEquals(2700.0, ProductService.parsePrice("2.700,00"));
        assertEquals(2700.5, ProductService.parsePrice("2700,50"));
        assertEquals(2700.0, ProductService.parsePrice("2700,00"));
        assertEquals(625.5, ProductService.parsePrice("625,50"));
        assertEquals(625.0, ProductService.parsePrice("625,0"));
        assertEquals(900.5, ProductService.parsePrice("900,5"));
        assertEquals(25.99, ProductService.parsePrice("$25,99"));
        assertEquals(2.7, ProductService.parsePrice("2,700")); // la coma actúa como decimal

        // Números enteros simples
        assertEquals(25.0, ProductService.parsePrice("25"));
        assertEquals(2600.0, ProductService.parsePrice("2600"));
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
