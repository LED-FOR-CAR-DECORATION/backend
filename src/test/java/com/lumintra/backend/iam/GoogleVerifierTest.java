package com.lumintra.backend.iam;

import com.lumintra.backend.iam.infrastructure.integration.GoogleIdTokenVerifier;
import com.lumintra.backend.shared.exception.iam.AuthFlowException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class GoogleVerifierTest {
    static KeyPair keys;
    GoogleIdTokenVerifier verifier;
    @BeforeAll static void keys() throws Exception { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); keys = generator.generateKeyPair(); }
    @BeforeEach void setup() { verifier = new GoogleIdTokenVerifier("test-client", NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build()); }

    String token(String issuer, String audience, String email, boolean verified, Instant expires) {
        return Jwts.builder().subject("google-subject").issuer(issuer).audience().add(audience).and()
                .claim("email", email).claim("email_verified", verified).claim("name", "Google User")
                .issuedAt(Date.from(Instant.now().minusSeconds(1))).expiration(Date.from(expires))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }
    void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthFlowException.class,
                error -> assertThat(error.getCode()).isEqualTo("INVALID_GOOGLE_TOKEN"));
    }
    @Test void verifiesRealRsaSignatureAndGoogleClaims() {
        var identity = verifier.verify(token("https://accounts.google.com", "test-client", "User@gmail.com", true, Instant.now().plusSeconds(300)));
        assertThat(identity.email()).isEqualTo("user@gmail.com"); assertThat(identity.subject()).isEqualTo("google-subject");
        assertThat(identity.authoritativeEmail()).isTrue();
    }
    @Test void rejectsWrongAudienceIssuerExpiredAndUnverifiedEmail() {
        assertInvalid(token("https://accounts.google.com", "other-client", "user@gmail.com", true, Instant.now().plusSeconds(300)));
        assertInvalid(token("https://evil.example", "test-client", "user@gmail.com", true, Instant.now().plusSeconds(300)));
        assertInvalid(token("https://accounts.google.com", "test-client", "user@gmail.com", true, Instant.now().minusSeconds(120)));
        assertInvalid(token("https://accounts.google.com", "test-client", "user@gmail.com", false, Instant.now().plusSeconds(300)));
    }
    @Test void rejectsForgedSignaturesAndMalformedTokens() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        String forged = Jwts.builder().subject("attacker").signWith(generator.generateKeyPair().getPrivate(), Jwts.SIG.RS256).compact();
        assertInvalid(forged); assertInvalid("not-a-jwt");
    }
    @Test void thirdPartyEmailIsNotAuthoritativeForLinking() {
        assertThat(verifier.verify(token("accounts.google.com", "test-client", "user@example.com", true, Instant.now().plusSeconds(300))).authoritativeEmail()).isFalse();
    }
    @Test void providerOutageAndMissingConfigurationAreServiceErrors() {
        var unavailable = new GoogleIdTokenVerifier("test-client", value -> { throw new JwtException("test key fetch timeout"); });
        assertThatThrownBy(() -> unavailable.verify("token")).isInstanceOfSatisfying(AuthFlowException.class,
                error -> assertThat(error.getCode()).isEqualTo("GOOGLE_UNAVAILABLE"));
        var unconfigured = new GoogleIdTokenVerifier("", value -> { throw new AssertionError("Should not call provider"); });
        assertThatThrownBy(() -> unconfigured.verify("token")).isInstanceOfSatisfying(AuthFlowException.class,
                error -> assertThat(error.getCode()).isEqualTo("GOOGLE_UNAVAILABLE"));
    }
}
