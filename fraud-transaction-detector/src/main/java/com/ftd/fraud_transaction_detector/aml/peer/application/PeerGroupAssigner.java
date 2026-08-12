package com.ftd.fraud_transaction_detector.aml.peer.application;

import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PeerGroupAssigner {

    private static final List<PeerGroupDefinition> OCCUPATION_GROUPS = List.of(
            PeerGroupDefinition.RETIREE,
            PeerGroupDefinition.STUDENT,
            PeerGroupDefinition.BUSINESS_OWNER,
            PeerGroupDefinition.MEDICAL,
            PeerGroupDefinition.TECHNOLOGY_ENGINEERING,
            PeerGroupDefinition.EDUCATION,
            PeerGroupDefinition.FINANCE_LEGAL,
            PeerGroupDefinition.MANAGEMENT_ADMINISTRATION,
            PeerGroupDefinition.SERVICE_OPERATIONS
    );

    public PeerGroupDefinition assign(String occupation, Integer age) {
        return candidates(occupation, age).get(0);
    }

    public List<PeerGroupDefinition> candidates(String occupation, Integer age) {
        PeerGroupDefinition occupationGroup = occupationGroup(occupation);
        PeerGroupDefinition ageGroup = ageGroup(age);
        List<PeerGroupDefinition> ordered = new ArrayList<>();
        if (occupationGroup != null && ageGroup != null) {
            ordered.add(PeerGroupDefinition.composite(occupationGroup, ageGroup));
        }
        if (occupationGroup != null) ordered.add(occupationGroup);
        if (ageGroup != null) ordered.add(ageGroup);
        ordered.add(PeerGroupDefinition.GLOBAL);

        Map<String, PeerGroupDefinition> unique = new LinkedHashMap<>();
        ordered.forEach(group -> unique.putIfAbsent(group.code(), group));
        return List.copyOf(unique.values());
    }

    private PeerGroupDefinition occupationGroup(String occupation) {
        String normalized = occupation == null ? "" : occupation.toLowerCase().trim();
        if (normalized.isEmpty()) return null;
        return OCCUPATION_GROUPS.stream()
                .filter(group -> group.occupationKeywords().stream().anyMatch(normalized::contains))
                .findFirst()
                .orElse(null);
    }

    private PeerGroupDefinition ageGroup(Integer age) {
        if (age == null || age < 0) return null;
        if (age < 18) return PeerGroupDefinition.AGE_UNDER_18;
        if (age <= 25) return PeerGroupDefinition.AGE_18_25;
        if (age <= 35) return PeerGroupDefinition.AGE_26_35;
        if (age <= 50) return PeerGroupDefinition.AGE_36_50;
        if (age <= 65) return PeerGroupDefinition.AGE_51_65;
        return PeerGroupDefinition.AGE_66_PLUS;
    }
}
