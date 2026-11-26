package com.lumintra.backend.iam.api.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class UpdateUserAddressRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID01 [N]: AC03 strips whitespace, preserves Unicode and validates Goong Map coordinates")
    void validRequest_normalizesAndPassesValidation() {
        var request =
                new UpdateUserAddressRequest(
                        "  Nguyễn Văn Bình  ",
                        "  +84912345678  ",
                        "  456 Lê Lợi, Bến Thành, Quận 1, TP.HCM  ",
                        new BigDecimal("10.773123456"),
                        new BigDecimal("106.698765432"));

        assertThat(request.recipientName()).isEqualTo("Nguyễn Văn Bình");
        assertThat(request.recipientPhone()).isEqualTo("+84912345678");
        assertThat(request.fullAddress()).isEqualTo("456 Lê Lợi, Bến Thành, Quận 1, TP.HCM");
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID02 [B]: AC03 accepts min and max length, phone digit and WGS84 coordinate boundaries")
    void boundaryValues_passValidation() {
        var minBoundaries =
                new UpdateUserAddressRequest(
                        "A",
                        "12345678",
                        "X",
                        new BigDecimal("-90.0"),
                        new BigDecimal("-180.0"));
        assertThat(VALIDATOR.validate(minBoundaries)).isEmpty();

        var maxBoundaries =
                new UpdateUserAddressRequest(
                        "A".repeat(100),
                        "+123456789012345",
                        "B".repeat(255),
                        new BigDecimal("90.0"),
                        new BigDecimal("180.0"));
        assertThat(VALIDATOR.validate(maxBoundaries)).isEmpty();
    }

    @Test
    @DisplayName("IAM-24 UTCID03 [A]: AC03 rejects null, blank or > 100 char recipientName")
    void invalidRecipientName_failsValidation() {
        for (String name : new String[] {null, "", "   ", "A".repeat(101)}) {
            var request =
                    new UpdateUserAddressRequest(
                            name,
                            "0912345678",
                            "456 Le Loi, District 1, HCMC",
                            new BigDecimal("10.7731235"),
                            new BigDecimal("106.6987654"));
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("IAM-24 UTCID04 [A]: AC03 rejects null, blank or invalid recipientPhone formats")
    void invalidRecipientPhone_failsValidation() {
        for (String phone :
                new String[] {
                    null,
                    "",
                    "   ",
                    "1234567",
                    "1234567890123456",
                    "091 234 5678",
                    "091-234-5678",
                    "091abc5678",
                    "++84912345678"
                }) {
            var request =
                    new UpdateUserAddressRequest(
                            "Nguyen Van B",
                            phone,
                            "456 Le Loi, District 1, HCMC",
                            new BigDecimal("10.7731235"),
                            new BigDecimal("106.6987654"));
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("IAM-24 UTCID05 [A]: AC03 rejects null, blank or > 255 char fullAddress")
    void invalidFullAddress_failsValidation() {
        for (String fullAddress : new String[] {null, "", "   ", "A".repeat(256)}) {
            var request =
                    new UpdateUserAddressRequest(
                            "Nguyen Van B",
                            "0912345678",
                            fullAddress,
                            new BigDecimal("10.7731235"),
                            new BigDecimal("106.6987654"));
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("IAM-24 UTCID06 [A]: AC03 rejects null or out-of-range latitude and longitude")
    void invalidCoordinates_failsValidation() {
        BigDecimal[][] invalidPairs = {
            {null, new BigDecimal("106.6987654")},
            {new BigDecimal("10.7731235"), null},
            {new BigDecimal("-90.000001"), new BigDecimal("106.6987654")},
            {new BigDecimal("90.000001"), new BigDecimal("106.6987654")},
            {new BigDecimal("10.7731235"), new BigDecimal("-180.000001")},
            {new BigDecimal("10.7731235"), new BigDecimal("180.000001")}
        };

        for (BigDecimal[] pair : invalidPairs) {
            var request =
                    new UpdateUserAddressRequest(
                            "Nguyen Van B",
                            "0912345678",
                            "456 Le Loi, District 1, HCMC",
                            pair[0],
                            pair[1]);
            assertThat(VALIDATOR.validate(request)).isNotEmpty();
        }
    }
}
