package com.trueapply.model;

/**
 * Applicant tracking systems whose forms TrueApply knows how to fill. Each one needs an
 * {@link com.trueapply.ats.ApplicationPlatform} registered in
 * {@link com.trueapply.ats.PlatformRegistry}; Greenhouse and Workday are implemented so far.
 */
public enum AtsType {
    GREENHOUSE("Greenhouse"),
    LEVER("Lever"),
    ASHBY("Ashby"),
    WORKDAY("Workday");

    private final String displayName;

    AtsType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
