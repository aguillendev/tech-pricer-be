package com.techpricer.service;

import com.techpricer.model.GlobalConfig;
import com.techpricer.model.Product;
import com.techpricer.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DolarService dolarService;

    @InjectMocks
    private ProductService productService;

    @Test
    void importProducts_ShouldParseAndSaveValidLines() {
        String input = "▪️IPHONE 15 128 GB - $ 625\n" +
                       "► HEADER IGNORED\n" +
                       "MACBOOK AIR M1 $ 900\n" + // checking flexible regex
                       "Simple Product - $ 100";

        productService.importProducts(input);

        verify(productRepository).deleteAll();
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> savedProducts = captor.getValue();
        assertEquals(3, savedProducts.size());

        // Check IPHONE
        Product p1 = savedProducts.get(0);
        assertEquals("IPHONE 15 128 GB", p1.getName());
        assertEquals(625, p1.getOriginalPriceUsd());

        // Check MACBOOK (The regex expects $ to be present. If my regex handles no separator, good.)
        // My regex was: (?:-|–)?\s*\$\s*
        // So "MACBOOK AIR M1 $ 900" -> separator matches empty string. Should work.
        Product p2 = savedProducts.get(1);
        assertEquals("MACBOOK AIR M1", p2.getName());
        assertEquals(900, p2.getOriginalPriceUsd());

        // Check Simple Product
        Product p3 = savedProducts.get(2);
        assertEquals("Simple Product", p3.getName());
        assertEquals(100, p3.getOriginalPriceUsd());
    }

    @Test
    void getAllProductsWithCalculatedPrice_ShouldCalculateCorrectly() {
        Product p = Product.builder().name("Test").originalPriceUsd(100).build();
        when(productRepository.findAll()).thenReturn(List.of(p));

        GlobalConfig config = GlobalConfig.builder().profitPercentage(20.0).build();
        when(dolarService.getConfig()).thenReturn(config);
        when(dolarService.getDolarVenta()).thenReturn(1000.0);

        List<Product> result = productService.getAllProductsWithCalculatedPrice();

        assertEquals(1, result.size());
        Product resP = result.get(0);
        // 100 * 1000 * 1.20 = 120000
        assertEquals(120000.0, resP.getFinalPriceArs());
    }
}
