package com.techpricer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Obtiene la cotización del dólar oficial directamente desde dolarapi.com
 * en cada llamada. No guarda ni cachea el valor en base de datos.
 *
 * Si la API no está disponible lanza DollarRateUnavailableException,
 * que el controller convierte en HTTP 503.
 */
@Service
@Slf4j
public class DolarService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_URL = "https://dolarapi.com/v1/dolares/oficial";

    /**
     * @return cotización de venta del dólar oficial (en ARS)
     * @throws DollarRateUnavailableException si la API no responde o devuelve datos
     *                                        inválidos
     */
    public Double getDolarVenta() {
        try {
            DolarApiResponse response = restTemplate.getForObject(API_URL, DolarApiResponse.class);
            if (response != null && response.venta() != null) {
                log.info("[DolarService] Cotización obtenida: ${}", response.venta());
                return response.venta();
            }
            throw new DollarRateUnavailableException("La API de cotización devolvió datos vacíos.");
        } catch (DollarRateUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("[DolarService] Error al obtener cotización: {}", e.getMessage());
            throw new DollarRateUnavailableException(
                    "No se pudo obtener la cotización del dólar. Verificá la conexión con dolarapi.com.");
        }
    }

    private record DolarApiResponse(Double compra, Double venta, String fechaActualizacion) {
    }

    // ── Excepción de dominio ──────────────────────────────────────────────────
    public static class DollarRateUnavailableException extends RuntimeException {
        public DollarRateUnavailableException(String message) {
            super(message);
        }
    }
}
