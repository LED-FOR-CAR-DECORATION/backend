package com.lumintra.backend.payment;

import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.infrastructure.integration.SePayGatewayAdapter;
import com.lumintra.backend.payment.infrastructure.integration.SePayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SePayGatewayAdapterTest {

    private SePayGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        SePayProperties properties = new SePayProperties(
                "https://vietqr.app/img/",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH",
                "lum",
                "webhook-secret"
        );
        adapter = new SePayGatewayAdapter(properties);
    }

    @Test
    @DisplayName("PAY-01 [N]: Generates uppercase alphanumeric payment code with configured prefix and target segment")
    void generatePaymentCode_validTarget_returnsPrefixedAlphanumericCode() {
        String orderCode = adapter.generatePaymentCode(PaymentTargetType.ORDER, 102L);
        String commissionCode = adapter.generatePaymentCode(PaymentTargetType.COMMISSION_INITIAL, 55L);
        String revisionCode = adapter.generatePaymentCode(PaymentTargetType.COMMISSION_REVISION, 77L);

        assertThat(orderCode).startsWith("LUMORD102").hasSize("LUMORD102".length() + 8);
        assertThat(commissionCode).startsWith("LUMCMS55").hasSize("LUMCMS55".length() + 8);
        assertThat(revisionCode).startsWith("LUMREV77").hasSize("LUMREV77".length() + 8);
        assertThat(orderCode).matches("^[A-Z0-9]+$");
    }

    @Test
    @DisplayName("PAY-01 [N]: Builds dynamic SePay VietQR image URL with template=compact, showinfo=true, and holder")
    void buildQrDetails_validInput_returnsFormattedQrUrlAndBankDetails() {
        SePayQrDetails details = adapter.buildQrDetails("LUMORD102ABCD1234", new BigDecimal("250000.00"));

        assertThat(details.paymentCode()).isEqualTo("LUMORD102ABCD1234");
        assertThat(details.bankName()).isEqualTo("TPBank");
        assertThat(details.accountNumber()).isEqualTo("10006140844");
        assertThat(details.accountName()).isEqualTo("NGUYEN QUANG KHANH");
        assertThat(details.qrUrl())
                .isEqualTo("https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LUMORD102ABCD1234&showinfo=true&holder=NGUYEN%20QUANG%20KHANH");
    }

    @Test
    @DisplayName("PAY-01 [A]: Rejects invalid targetId or blank paymentCode")
    void generateAndBuild_invalidInputs_throwIllegalArgumentException() {
        assertThatThrownBy(() -> adapter.generatePaymentCode(PaymentTargetType.ORDER, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.buildQrDetails("   ", new BigDecimal("100000")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
