package com.techpricer.service;

import com.techpricer.model.GlobalConfig;
import com.techpricer.repository.GlobalConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DolarService {

    private final GlobalConfigRepository configRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_URL = "https://dolarapi.com/v1/dolares/blue";
    private static final Double DEFAULT_DOLAR = 1000.0;

    @PostConstruct
    public void init() {
        fetchAndSaveDolar();
    }

    public Double getDolarVenta() {
        GlobalConfig config = getConfig();
        if (config.getLastApiDollarValue() != null) {
            return config.getLastApiDollarValue();
        }
        if (config.getManualDollarValue() != null) {
            return config.getManualDollarValue();
        }
        return DEFAULT_DOLAR;
    }

    public GlobalConfig getConfig() {
        return configRepository.findById(1L).orElseGet(() -> {
            GlobalConfig config = GlobalConfig.builder()
                    .id(1L)
                    .profitPercentage(0.0)
                    .manualDollarValue(DEFAULT_DOLAR)
                    .build();
            return configRepository.save(config);
        });
    }

    public void fetchAndSaveDolar() {
        try {
            DolarApiResponse response = restTemplate.getForObject(API_URL, DolarApiResponse.class);
            if (response != null && response.venta() != null) {
                GlobalConfig config = getConfig();
                config.setLastApiDollarValue(response.venta());
                config.setLastUpdated(LocalDateTime.now());
                configRepository.save(config);
                log.info("Dolar actualizado: {}", response.venta());
            }
        } catch (Exception e) {
            log.error("Error al obtener cotizacion del dolar: {}", e.getMessage());
            // Fallback logic is handled in getDolarVenta by checking nulls or old values
        }
    }

    private record DolarApiResponse(Double compra, Double venta, String fechaActualizacion) {}
}
