package com.techpricer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @com.fasterxml.jackson.annotation.JsonProperty("priceUsd")
    private Double originalPriceUsd;

    private String category;

    // Storing it as per requirement, but ideally should be calculated or cached.
    // We will update this whenever we recalculate prices.
    private Double finalPriceArs;
}
