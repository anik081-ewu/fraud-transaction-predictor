package com.ftd.fraud_transaction_detector.aml.peer.domain;

import java.util.List;

public record PeerGroupDefinition(
        String code,
        List<String> occupationKeywords,
        Integer minAge,
        Integer maxAge,
        boolean ageFallback,
        int ageFallbackThreshold,
        String customerType
) {

    public static final PeerGroupDefinition RETIREE = occupation(
            "RETIREE", List.of("retired", "retiree", "pensioner"), "RETAIL"
    );
    public static final PeerGroupDefinition STUDENT = occupation(
            "STUDENT", List.of("student", "intern", "apprentice", "trainee"), "RETAIL"
    );
    public static final PeerGroupDefinition BUSINESS_OWNER = occupation(
            "BUSINESS_OWNER",
            List.of("business owner", "entrepreneur", "self-employed", "self employed",
                    "freelance", "proprietor", "founder", "chairman", "ceo"),
            "BUSINESS"
    );
    public static final PeerGroupDefinition MEDICAL = occupation(
            "MEDICAL",
            List.of("doctor", "physician", "surgeon", "nurse", "pharmacist", "therapist",
                    "dentist", "medical", "healthcare", "paramedic"),
            "RETAIL"
    );
    public static final PeerGroupDefinition TECHNOLOGY_ENGINEERING = occupation(
            "TECHNOLOGY_ENGINEERING",
            List.of("engineer", "developer", "programmer", "software", "technology", "technician",
                    "architect", "data scientist", "it specialist", "system administrator"),
            "RETAIL"
    );
    public static final PeerGroupDefinition EDUCATION = occupation(
            "EDUCATION",
            List.of("teacher", "professor", "lecturer", "educator", "academic", "principal"),
            "RETAIL"
    );
    public static final PeerGroupDefinition FINANCE_LEGAL = occupation(
            "FINANCE_LEGAL",
            List.of("accountant", "auditor", "banker", "finance", "financial", "lawyer",
                    "attorney", "analyst"),
            "RETAIL"
    );
    public static final PeerGroupDefinition MANAGEMENT_ADMINISTRATION = occupation(
            "MANAGEMENT_ADMINISTRATION",
            List.of("manager", "officer", "director", "executive", "supervisor", "administrator",
                    "consultant", "partner"),
            "RETAIL"
    );
    public static final PeerGroupDefinition SERVICE_OPERATIONS = occupation(
            "SERVICE_OPERATIONS",
            List.of("clerk", "cashier", "sales", "driver", "operator", "service", "security",
                    "hospitality", "mechanic", "technician"),
            "RETAIL"
    );

    public static final PeerGroupDefinition AGE_UNDER_18 = age("AGE_UNDER_18", 0, 17);
    public static final PeerGroupDefinition AGE_18_25 = age("AGE_18_25", 18, 25);
    public static final PeerGroupDefinition AGE_26_35 = age("AGE_26_35", 26, 35);
    public static final PeerGroupDefinition AGE_36_50 = age("AGE_36_50", 36, 50);
    public static final PeerGroupDefinition AGE_51_65 = age("AGE_51_65", 51, 65);
    public static final PeerGroupDefinition AGE_66_PLUS = age("AGE_66_PLUS", 66, null);

    public static final PeerGroupDefinition GLOBAL = new PeerGroupDefinition(
            "GLOBAL", List.of(), null, null, false, 0, "RETAIL"
    );

    public PeerGroupDefinition {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        code = code.trim().toUpperCase();
        occupationKeywords = occupationKeywords == null ? List.of() : List.copyOf(occupationKeywords);
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalArgumentException("minAge cannot exceed maxAge");
        }
        customerType = customerType == null || customerType.isBlank() ? "RETAIL" : customerType.trim();
    }

    public static PeerGroupDefinition composite(
            PeerGroupDefinition occupationGroup,
            PeerGroupDefinition ageGroup
    ) {
        return new PeerGroupDefinition(
                occupationGroup.code() + "_" + ageGroup.code(),
                occupationGroup.occupationKeywords(),
                ageGroup.minAge(), ageGroup.maxAge(),
                false, 0, occupationGroup.customerType()
        );
    }

    public boolean isGlobal() {
        return "GLOBAL".equals(code);
    }

    public boolean isComposite() {
        return !occupationKeywords.isEmpty() && (minAge != null || maxAge != null);
    }

    private static PeerGroupDefinition occupation(String code, List<String> keywords, String customerType) {
        return new PeerGroupDefinition(code, keywords, null, null, false, 0, customerType);
    }

    private static PeerGroupDefinition age(String code, int minAge, Integer maxAge) {
        return new PeerGroupDefinition(code, List.of(), minAge, maxAge, true, minAge, "RETAIL");
    }
}
