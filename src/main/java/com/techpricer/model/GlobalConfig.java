package com.techpricer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "global_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalConfig {

    @Id
    private Long id; // Will use 1 for singleton

    private Double profitPercentage;

    private Double manualDollarValue; // Fallback or manual override

    private Double lastApiDollarValue; // Last successful API fetch

    private LocalDateTime lastUpdated;
}
