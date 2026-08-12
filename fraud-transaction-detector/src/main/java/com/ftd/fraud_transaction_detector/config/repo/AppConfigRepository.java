package com.ftd.fraud_transaction_detector.config.repo;

import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
}

