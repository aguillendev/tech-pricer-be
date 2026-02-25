package com.techpricer.repository;

import com.techpricer.model.ProfitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfitRuleRepository extends JpaRepository<ProfitRule, Long> {

    // Trae las reglas ordenadas por minPriceUsd ascendente para resolución
    // complementaria
    List<ProfitRule> findAllByOrderByMinPriceUsdAsc();
}
