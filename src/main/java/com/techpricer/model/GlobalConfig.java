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
    private Long id; // Singleton: siempre id=1

    private Double profitPercentage;

    private LocalDateTime lastUpdated;
}
