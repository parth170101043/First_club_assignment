package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads and updates the singleton behavioral-tier configuration.
 */
@Service
public class BehavioralTierSettingsService {

    public static final UUID SETTINGS_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final BehavioralTierSettingsRepository repository;

    public BehavioralTierSettingsService(BehavioralTierSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public View current() {
        return View.from(requireSettings());
    }

    @Transactional
    public View update(UpdateRequest request) {
        validate(request);
        BehavioralTierSettings settings = requireSettings();
        settings.update(
            request.goldOrderCount(),
            request.platinumOrderCount(),
            request.goldMonthlySpend(),
            request.platinumMonthlySpend(),
            request.goldCohort().trim(),
            request.platinumCohort().trim()
        );
        return View.from(settings);
    }

    private BehavioralTierSettings requireSettings() {
        return repository.findById(SETTINGS_ID)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Behavioral tier settings are not initialized."));
    }

    private void validate(UpdateRequest request) {
        if (request.goldOrderCount() < 0
            || request.platinumOrderCount() < request.goldOrderCount()) {
            throw new IllegalArgumentException(
                "Platinum order count must be at least the Gold order count.");
        }
        if (request.goldMonthlySpend() == null
            || request.platinumMonthlySpend() == null
            || request.goldMonthlySpend().signum() < 0
            || request.platinumMonthlySpend()
                .compareTo(request.goldMonthlySpend()) < 0) {
            throw new IllegalArgumentException(
                "Platinum monthly spend must be at least the Gold monthly spend.");
        }
        if (request.goldCohort() == null || request.goldCohort().isBlank()
            || request.platinumCohort() == null
            || request.platinumCohort().isBlank()) {
            throw new IllegalArgumentException("Cohort names are required.");
        }
        if (request.goldCohort().trim()
            .equalsIgnoreCase(request.platinumCohort().trim())) {
            throw new IllegalArgumentException(
                "Gold and Platinum cohort names must be different.");
        }
    }

    public record UpdateRequest(
        long goldOrderCount,
        long platinumOrderCount,
        BigDecimal goldMonthlySpend,
        BigDecimal platinumMonthlySpend,
        String goldCohort,
        String platinumCohort
    ) {
    }

    public record View(
        long goldOrderCount,
        long platinumOrderCount,
        BigDecimal goldMonthlySpend,
        BigDecimal platinumMonthlySpend,
        String goldCohort,
        String platinumCohort
    ) {
        static View from(BehavioralTierSettings settings) {
            return new View(
                settings.getGoldOrderCount(),
                settings.getPlatinumOrderCount(),
                settings.getGoldMonthlySpend(),
                settings.getPlatinumMonthlySpend(),
                settings.getGoldCohort(),
                settings.getPlatinumCohort()
            );
        }
    }
}
