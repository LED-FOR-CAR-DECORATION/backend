package com.lumintra.backend.commission.domain.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Option codes seeded with the locked system pricing rules. They drive pricing and preset logic, so
 * they can be relabelled and repriced but never removed or deactivated. Options appended later by
 * staff are not core and can be removed.
 */
public final class SystemPricingRules {

    public static final String FORMAT_TYPE = "FORMAT_TYPE";
    public static final String MATRIX_SIZE = "MATRIX_SIZE";
    public static final String FRAME_RANGE = "FRAME_RANGE";

    private static final Map<String, Set<String>> CORE_OPTION_CODES = Map.of(
            FORMAT_TYPE, Set.of("STATIC_IMAGE", "ANIMATED_GIF"),
            MATRIX_SIZE, Set.of("32X32", "32X64", "64X64"),
            FRAME_RANGE, Set.of("1-5", "6-15", "16-30"));

    private SystemPricingRules() {
    }

    public static boolean isCoreOption(String ruleKey, String optionCode) {
        if (ruleKey == null || optionCode == null) {
            return false;
        }
        return CORE_OPTION_CODES.getOrDefault(ruleKey, Set.of())
                .contains(optionCode.trim().toUpperCase(Locale.ROOT));
    }
}
