package com.techpricer.service;

import com.techpricer.model.ProfitRule;
import com.techpricer.repository.ProfitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitRuleService {

    private final ProfitRuleRepository profitRuleRepository;

    /**
     * Devuelve todas las reglas ordenadas por minPriceUsd ascendente.
     */
    public List<ProfitRule> getAllRules() {
        return profitRuleRepository.findAllByOrderByMinPriceUsdAsc();
    }

    /**
     * Crea una nueva regla.
     */
    @Transactional
    public ProfitRule createRule(ProfitRule rule) {
        return profitRuleRepository.save(rule);
    }

    /**
     * Actualiza una regla existente.
     */
    @Transactional
    public ProfitRule updateRule(Long id, ProfitRule updatedRule) {
        ProfitRule existing = profitRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Regla no encontrada con id: " + id));
        existing.setMinPriceUsd(updatedRule.getMinPriceUsd());
        existing.setMaxPriceUsd(updatedRule.getMaxPriceUsd());
        existing.setProfitPercentage(updatedRule.getProfitPercentage());
        existing.setDescription(updatedRule.getDescription());
        return profitRuleRepository.save(existing);
    }

    /**
     * Elimina una regla por id.
     */
    @Transactional
    public void deleteRule(Long id) {
        profitRuleRepository.deleteById(id);
    }

    /**
     * Resuelve el porcentaje de ganancia a aplicar a un producto dado su precio en
     * USD.
     *
     * Usa rangos explícitos: minPriceUsd (inclusive) y maxPriceUsd (inclusive).
     * Si maxPriceUsd es null, no hay límite superior.
     * Si minPriceUsd es null, no hay límite inferior (desde $0).
     *
     * Ejemplo:
     * - Regla A: min=0, max=500, profit=15% → aplica a $0–$500
     * - Regla B: min=501, max=1000, profit=12% → aplica a $501–$1000
     * - Regla C: min=1001, max=null, profit=10% → aplica a $1001+
     *
     * Si no hay reglas, retorna null (se usará el profitPercentage global).
     */
    public Double resolveProfit(Double priceUsd, List<ProfitRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (ProfitRule rule : rules) {
            boolean aboveMin = (rule.getMinPriceUsd() == null || priceUsd >= rule.getMinPriceUsd());
            boolean belowMax = (rule.getMaxPriceUsd() == null || priceUsd <= rule.getMaxPriceUsd());

            if (aboveMin && belowMax) {
                return rule.getProfitPercentage();
            }
        }

        return null;
    }
}
