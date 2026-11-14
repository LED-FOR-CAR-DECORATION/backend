package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.domain.model.CarrierDispatchCommand;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchResult;
import com.lumintra.backend.commerce.infrastructure.integration.AhamoveProperties;
import com.lumintra.backend.commerce.infrastructure.integration.AhamoveShippingAdapter;
import com.lumintra.backend.shared.exception.commerce.CarrierDispatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AhamoveShippingAdapterTest {

    private static final String BASE_URL = "https://partner-apistg.ahamove.com/v3";

    private AhamoveProperties properties(String paymentMethod, String pickupMobile) {
        return new AhamoveProperties(
                BASE_URL,
                "aha-stg-key",
                "84901112222",
                paymentMethod,
                new AhamoveProperties.Pickup(
                        "Lumintra Warehouse",
                        pickupMobile,
                        "7/28 Thanh Thai, Phuong 14, Quan 10, TP Ho Chi Minh",
                        new BigDecimal("10.7697535"),
                        new BigDecimal("106.6636615")
                )
        );
    }

    private CarrierDispatchCommand sampleCommand(String serviceId, String remarks, BigDecimal weightKg) {
        return new CarrierDispatchCommand(
                102L,
                serviceId,
                remarks,
                weightKg,
                "LUM-102-ABCD1234",
                "Dang Phuoc Nguyen",
                "0912345678",
                "475A Dien Bien Phu, Binh Thanh, HCMC",
                new BigDecimal("10.8018493"),
                new BigDecimal("106.7144660"),
                List.of(new CarrierDispatchCommand.DispatchItem(
                        5L,
                        "Lumintra LED Matrix v1",
                        2,
                        new BigDecimal("400000")
                ))
        );
    }

    @Test
    @DisplayName("COM-13 UTCID01 [N]: Authenticates once, caches token across calls, sends service_id + BALANCE + cod=0 + package_detail, and parses tracking result")
    void createDeliveryOrder_serviceIdWithBalanceAndCachedToken_succeeds() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AhamoveShippingAdapter adapter = new AhamoveShippingAdapter(builder.build(), properties(null, "84909998888"));

        server.expect(once(), requestTo(BASE_URL + "/accounts/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"mobile\":\"84901112222\"")))
                .andExpect(content().string(containsString("\"api_key\":\"aha-stg-key\"")))
                .andRespond(withSuccess("{\"token\":\"token-1\",\"refresh_token\":\"ref-1\"}", MediaType.APPLICATION_JSON));

        server.expect(times(2), requestTo(BASE_URL + "/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andExpect(content().string(containsString("\"service_id\":\"SGN-BIKE\"")))
                .andExpect(content().string(containsString("\"payment_method\":\"BALANCE\"")))
                .andExpect(content().string(containsString("\"cod\":0")))
                .andExpect(content().string(containsString("\"tracking_number\":\"LUM-102-ABCD1234\"")))
                .andExpect(content().string(containsString("\"weight\":1.5")))
                .andExpect(content().string(containsString("\"price\":400000")))
                .andRespond(withSuccess("""
                        {
                          "order_id": "24ABCD",
                          "status": "ASSIGNING",
                          "shared_link": "https://expressstg.ahamove.com/s/24ABCD",
                          "order": {
                            "total_fee": 33000
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CarrierDispatchResult first = adapter.createDeliveryOrder(
                sampleCommand("SGN-BIKE", "Handle fragile package", new BigDecimal("1.5"))
        );
        CarrierDispatchResult second = adapter.createDeliveryOrder(
                sampleCommand("SGN-BIKE", "Handle fragile package", new BigDecimal("1.5"))
        );

        assertThat(first.trackingNumber()).isEqualTo("24ABCD");
        assertThat(first.trackingUrl()).isEqualTo("https://expressstg.ahamove.com/s/24ABCD");
        assertThat(first.carrierFee()).isEqualByComparingTo("33000");
        assertThat(first.providerStatus()).isEqualTo("ASSIGNING");
        assertThat(second.trackingNumber()).isEqualTo("24ABCD");
        server.verify();
    }

    @Test
    @DisplayName("COM-13 UTCID02 [N]: Sends group_service_id + CASH payment_method, falls back pickup mobile, and omits package_detail when weight is null")
    void createDeliveryOrder_groupServiceIdWithCashAndNoWeight_succeeds() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AhamoveShippingAdapter adapter = new AhamoveShippingAdapter(builder.build(), properties("CASH", ""));

        server.expect(once(), requestTo(BASE_URL + "/accounts/token"))
                .andRespond(withSuccess("{\"token\":\"token-cash\"}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andExpect(content().string(containsString("\"group_service_id\":\"BIKE\"")))
                .andExpect(content().string(containsString("\"group_requests\":[]")))
                .andExpect(content().string(containsString("\"payment_method\":\"CASH\"")))
                .andExpect(content().string(containsString("\"mobile\":\"84901112222\"")))
                .andExpect(content().string(not(containsString("\"service_id\""))))
                .andExpect(content().string(not(containsString("\"package_detail\""))))
                .andRespond(withSuccess("""
                        {
                          "order_id": "24GRP1",
                          "status": "ASSIGNING",
                          "shared_link": "https://expressstg.ahamove.com/s/24GRP1",
                          "order": {
                            "total_price": 29000
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CarrierDispatchResult result = adapter.createDeliveryOrder(sampleCommand("BIKE", null, null));

        assertThat(result.trackingNumber()).isEqualTo("24GRP1");
        assertThat(result.carrierFee()).isEqualByComparingTo("29000");
        server.verify();
    }

    @Test
    @DisplayName("COM-13 UTCID03 [N]: Refreshes cached Bearer token once on 401 from /orders and retries successfully")
    void createDeliveryOrder_on401_refreshesTokenAndRetriesOnce() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AhamoveShippingAdapter adapter = new AhamoveShippingAdapter(builder.build(), properties("BALANCE", "84909998888"));

        server.expect(once(), requestTo(BASE_URL + "/accounts/token"))
                .andRespond(withSuccess("{\"token\":\"expired-token\"}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NOT_AUTHORIZED\",\"description\":\"Token expired\"}"));

        server.expect(once(), requestTo(BASE_URL + "/accounts/token"))
                .andRespond(withSuccess("{\"token\":\"fresh-token\"}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh-token"))
                .andRespond(withSuccess("""
                        {
                          "order_id": "24RETRY",
                          "status": "ASSIGNING",
                          "shared_link": "https://expressstg.ahamove.com/s/24RETRY",
                          "order": { "total_fee": 31000 }
                        }
                        """, MediaType.APPLICATION_JSON));

        CarrierDispatchResult result = adapter.createDeliveryOrder(sampleCommand("VAN-500", null, null));

        assertThat(result.trackingNumber()).isEqualTo("24RETRY");
        server.verify();
    }

    @Test
    @DisplayName("COM-13 UTCID04 [A]: Maps AhaMove 400/404/406/409/503, network timeout, and missing config to CarrierDispatchException")
    void createDeliveryOrder_providerErrorsAndMissingConfig_mapToCarrierDispatchException() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AhamoveShippingAdapter adapter = new AhamoveShippingAdapter(builder.build(), properties("BALANCE", "84909998888"));

        server.expect(once(), requestTo(BASE_URL + "/accounts/token"))
                .andRespond(withSuccess("{\"token\":\"valid-token\"}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.NOT_ACCEPTABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NOT_ENOUGH_CREDIT\",\"description\":\"Insufficient wallet balance\"}"));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"SERVICE_NOT_FOUND\",\"description\":\"Service does not exist\"}"));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"DUPLICATE_TRACKING_NUMBER\",\"description\":\"Duplicate tracking number\"}"));

        server.expect(once(), requestTo(BASE_URL + "/orders"))
                .andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> adapter.createDeliveryOrder(sampleCommand("SGN-BIKE", null, null)))
                .isInstanceOf(CarrierDispatchException.class)
                .satisfies(ex -> {
                    CarrierDispatchException cde = (CarrierDispatchException) ex;
                    assertThat(cde.getHttpStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
                    assertThat(cde.getErrorCode()).isEqualTo("NOT_ENOUGH_CREDIT");
                });

        assertThatThrownBy(() -> adapter.createDeliveryOrder(sampleCommand("SGN-INVALID", null, null)))
                .isInstanceOf(CarrierDispatchException.class)
                .satisfies(ex -> {
                    CarrierDispatchException cde = (CarrierDispatchException) ex;
                    assertThat(cde.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(cde.getErrorCode()).isEqualTo("SERVICE_NOT_FOUND");
                });

        assertThatThrownBy(() -> adapter.createDeliveryOrder(sampleCommand("SGN-BIKE", null, null)))
                .isInstanceOf(CarrierDispatchException.class)
                .satisfies(ex -> {
                    CarrierDispatchException cde = (CarrierDispatchException) ex;
                    assertThat(cde.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(cde.getErrorCode()).isEqualTo("DUPLICATE_TRACKING_NUMBER");
                });

        assertThatThrownBy(() -> adapter.createDeliveryOrder(sampleCommand("SGN-BIKE", null, null)))
                .isInstanceOf(CarrierDispatchException.class)
                .satisfies(ex -> {
                    CarrierDispatchException cde = (CarrierDispatchException) ex;
                    assertThat(cde.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(cde.getErrorCode()).isEqualTo("CARRIER_UNAVAILABLE");
                });

        AhamoveShippingAdapter unconfigured = new AhamoveShippingAdapter(
                builder.build(),
                new AhamoveProperties(BASE_URL, "", "84901112222", "BALANCE", null)
        );
        assertThatThrownBy(() -> unconfigured.createDeliveryOrder(sampleCommand("SGN-BIKE", null, null)))
                .isInstanceOf(CarrierDispatchException.class)
                .satisfies(ex -> {
                    CarrierDispatchException cde = (CarrierDispatchException) ex;
                    assertThat(cde.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(cde.getErrorCode()).isEqualTo("CARRIER_NOT_CONFIGURED");
                });

        server.verify();
    }
}
