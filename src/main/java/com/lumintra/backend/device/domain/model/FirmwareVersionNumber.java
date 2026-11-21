package com.lumintra.backend.device.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic firmware version {@code MAJOR.MINOR.PATCH} without leading zeros, ordered numerically
 * (so {@code 1.10.0 > 1.9.0}).
 */
public record FirmwareVersionNumber(int major, int minor, int patch) implements Comparable<FirmwareVersionNumber> {

    /** Same rule as the request validation; each part is at most 6 digits so it fits an int. */
    public static final String PATTERN = "^(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})\\.(0|[1-9]\\d{0,5})$";
    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public static FirmwareVersionNumber parse(String raw) {
        Matcher matcher = raw == null ? null : COMPILED.matcher(raw.strip());
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Firmware version must follow MAJOR.MINOR.PATCH: " + raw);
        }
        return new FirmwareVersionNumber(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    /** Lenient variant for stored rows; an unparseable legacy value yields null. */
    public static FirmwareVersionNumber tryParse(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public int compareTo(FirmwareVersionNumber other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        return result;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
