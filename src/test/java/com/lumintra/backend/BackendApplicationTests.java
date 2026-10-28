package com.lumintra.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.lumintra.backend.iam.infrastructure.bootstrap.IamDataSeeder;
import com.lumintra.backend.notification.infrastructure.integration.ResendEmailClient;
import com.lumintra.backend.shared.caching.SafeCacheService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-application-secret-at-least-thirty-two-bytes",
        "google.client.id=", "spring.jpa.show-sql=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BackendApplicationTests {
    @MockitoBean IamDataSeeder seeder;
    @MockitoBean ResendEmailClient emailClient;
    @MockitoBean SafeCacheService cache;
    @Autowired MockMvc mvc;

    @Test void openApiContainsAllAuthEndpointsAndCorrectRequestFields() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/google'].post").exists())
                .andExpect(jsonPath("$.paths['/api/management/auth/google'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/verify-email'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/resend-verification'].post").exists())
                .andExpect(jsonPath("$.paths['/api/management/auth/login']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.fullName").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

	@Test
	void contextLoads() {
	}

}
