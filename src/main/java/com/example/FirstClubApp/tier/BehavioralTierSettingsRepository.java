package com.example.FirstClubApp.tier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BehavioralTierSettingsRepository
    extends JpaRepository<BehavioralTierSettings, UUID> {
}
