package com.lumintra.backend.iam.api.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ManagedUserFilterRequestTest {
    static final ValidatorFactory factory=Validation.buildDefaultValidatorFactory();
    static final Validator validator=factory.getValidator();
    @AfterAll static void close(){factory.close();}

    @Test @DisplayName("N: Default pagination and blank filters normalize consistently")
    void defaults() {
        var request=new ManagedUserFilterRequest(null,null," ",null," ");
        assertThat(request.page()).isZero();assertThat(request.size()).isEqualTo(10);
        assertThat(request.role()).isNull();assertThat(request.search()).isNull();assertThat(validator.validate(request)).isEmpty();
    }

    @Test @DisplayName("N: Role and search trim with locale-independent case normalization")
    void normalization() {
        var request=new ManagedUserFilterRequest(0,10," manager ",true," Nguyen ");
        assertThat(request.role()).isEqualTo("MANAGER");assertThat(request.search()).isEqualTo("Nguyen");
    }

    @Test @DisplayName("B: Pagination accepts minimum and maximum supported values")
    void boundaries() {
        assertThat(validator.validate(new ManagedUserFilterRequest(0,1,null,null,null))).isEmpty();
        assertThat(validator.validate(new ManagedUserFilterRequest(1000000,50,null,null,null))).isEmpty();
    }

    @Test @DisplayName("A: Reject negative pages, excessive offsets and invalid page sizes")
    void invalidPagination() {
        for(var request:new ManagedUserFilterRequest[]{new ManagedUserFilterRequest(-1,10,null,null,null),
                new ManagedUserFilterRequest(1000001,10,null,null,null),new ManagedUserFilterRequest(0,0,null,null,null),
                new ManagedUserFilterRequest(0,51,null,null,null)}) assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test @DisplayName("A: Oversized role and search inputs are rejected")
    void invalidLengths() {
        assertThat(validator.validate(new ManagedUserFilterRequest(0,10,"A".repeat(101),null,null))).isNotEmpty();
        assertThat(validator.validate(new ManagedUserFilterRequest(0,10,null,null,"A".repeat(256)))).isNotEmpty();
    }
}
