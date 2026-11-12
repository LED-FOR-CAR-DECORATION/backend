package com.lumintra.backend.iam.api.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class AddUserAddressRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID01 [N]: AC03 strips whitespace, preserves Unicode and validates Goong Map coordinates")
    void validRequest_normalizesAndPassesValidation() {
        var request =
                new AddUserAddressRequest(
                        "  Nguyễn Văn An  ",
                        "  +84901234567  ",
                        "  123 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM  ",
                        new BigDecimal("10.7768891234"),
                        new BigDecimal("106.7008065678"),
                        true);

        assertThat(request.recipientName()).isEqualTo("Nguyễn Văn An");
        assertThat(request.recipientPhone()).isEqualTo("+84901234567");
        assertThat(request.fullAddress()).isEqualTo("123 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM");
        assertThat(request.isDefault()).isTrue();
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID02 [B]: AC03 defaults null isDefault to false and accepts field length & coordinate boundaries")
    void boundaryValues_normalizeAndPassValidation() {
        var omittedDefault =
                new AddUserAddressRequest(
                        "A",
                        "12345678",
                        "X",
                        new BigDecimal("-90.0"),
                        new BigDecimal("-180.0"),
                        null);
        assertThat(omittedDefault.isDefault()).isFalse();
        assertThat(VALIDATOR.validate(omittedDefault)).isEmpty();

        var maxBoundaries =
                new AddUserAddressRequest(
                        "A".repeat(100),
                        "+123456789012345",
                        "B".repeat(255),
                        new BigDecimal("90.0"),
                        new BigDecimal("180.0"),
                        false);
        assertThat(maxBoundaries.isDefault()).isFalse();
        assertThat(VALIDATOR.validate(maxBoundaries)).isEmpty();
    }

    @Test
    @DisplayName("IAM-23 UTCID03 [A]: AC03 rejects null, blank or > 100 char recipientName")
    void invalidRecipientName_failsValidation() {
        for (String name : new String[] {null, "", "   ", "A".repeat(101)}) {
            var request =
                    new AddUserAddressRequest(
                            name,
                            "0901234567",
                            "123 Nguyen Hue, District 1, HCMC",
                            new BigDecimal("10.776889"),
                            new BigDecimal("106.700806"),
                            false);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("IAM-23 UTCID04 [A]: AC03 rejects missing or invalid recipientPhone formats")
    void invalidRecipientPhone_failsValidation() {
        for (String phone :
                new String[] {
                    null,
                    "",
                    "   ",
                    "1234567",
                    "1234567890123456",
                    "090 123 4567",
                    "090-123-4567",
                    "090abc4567",
                    "++84901234567"
                }) {
            var request =
                    new AddUserAddressRequest(
                            "Nguyen Van A",
                            phone,
                            "123 Nguyen Hue, District 1, HCMC",
                            new BigDecimal("10.776889"),
                            new BigDecimal("106.700806"),
                            false);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("IAM-23 UTCID05 [A]: AC03 rejects null, blank or > 255 char fullAddress")
    void invalidFullAddress_failsValidation() {
        for (String fullAddress : new String[] {null, "", "   ", "A".repeat(256)}) {
            var request =
                    new AddUserAddressRequest(
                            "Nguyen Van A",
                            "0901234567",
                            fullAddress,
                            new BigDecimal("10.776889"),
                            new BigDecimal("106.700806"),
                            false);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("IAM-23 UTCID06 [A]: AC03 rejects null or out-of-range latitude and longitude")
    void invalidCoordinates_failsValidation() {
        BigDecimal[][] invalidPairs = {
            {null, new BigDecimal("106.700806")},
            {new BigDecimal("10.776889"), null},
            {new BigDecimal("-90.000001"), new BigDecimal("106.700806")},
            {new BigDecimal("90.000001"), new BigDecimal("106.700806")},
            {new BigDecimal("10.776889"), new BigDecimal("-180.000001")},
            {new BigDecimal("10.776889"), new BigDecimal("180.000001")}
        };

        for (BigDecimal[] pair : invalidPairs) {
            var request =
                    new AddUserAddressRequest(
                            "Nguyen Van A",
                            "0901234567",
                            "123 Nguyen Hue, District 1, HCMC",
                            pair[0],
                            pair[1],
                            false);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }
}
