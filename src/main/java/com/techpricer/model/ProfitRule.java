package com.techpricer.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "profit_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Precio mínimo en USD (inclusive) para aplicar esta regla.
     * null = sin límite inferior.
     */
    private Double minPriceUsd;

    /**
     * Precio máximo en USD (exclusive) para aplicar esta regla.
     * null = sin límite superior.
     */
    private Double maxPriceUsd;

    /**
     * Porcentaje de ganancia a aplicar (ej: 15 = 15%).
     */
    private Double profitPercentage;

    /**
     * Descripción opcional de la regla (ej: "Menos de 500 USD").
     */
    private String description;
}
