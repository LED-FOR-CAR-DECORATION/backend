package com.lumintra.backend.firmware;

import com.lumintra.backend.firmware.api.request.FirmwareRequests;
import com.lumintra.backend.firmware.application.FirmwareUseCases;
import com.lumintra.backend.firmware.domain.model.FirmwareException;
import com.lumintra.backend.firmware.domain.service.FirmwarePolicy;
import com.lumintra.backend.firmware.infrastructure.persistence.repository.*;
import com.lumintra.backend.iam.application.AccountLookup;
import com.lumintra.backend.iam.infrastructure.bootstrap.IamDataSeeder;
import com.lumintra.backend.notification.infrastructure.integration.ResendEmailClient;
import com.lumintra.backend.shared.caching.SafeCacheService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:firmware;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.jwt.secret=test-firmware-secret-at-least-thirty-two-bytes", "google.client.id=", "spring.jpa.show-sql=false"
})
@ActiveProfiles("test") @AutoConfigureMockMvc
class FirmwareFlowTest {
    @MockitoBean IamDataSeeder seeder;
    @MockitoBean ResendEmailClient emailClient;
    @MockitoBean SafeCacheService cache;
    @MockitoBean AccountLookup accounts;
    @MockitoBean com.lumintra.backend.iam.application.TokenSessionService tokenSessions;
    @Autowired com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider jwt;
    @Autowired FirmwareUseCases firmware;
    @Autowired FirmwareSessionRepository sessions;
    @Autowired FirmwareDeviceRepository devices;
    @Autowired FirmwareChannelRepository channels;
    @Autowired FirmwareReleaseRepository releases;
    @Autowired MockMvc mvc;
    @BeforeEach void setup() {
        sessions.deleteAll(); devices.deleteAll(); channels.deleteAll(); releases.deleteAll();
        when(accounts.isActive(1L)).thenReturn(true);
        when(tokenSessions.authenticate(anyString())).thenAnswer(call -> jwt.getAuthentication(call.getArgument(0)));
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    static byte[] image(String version,String hardware) {
        byte[] bytes=new byte[1040]; bytes[0]=(byte)0xe9; bytes[1]=1;
        java.nio.ByteBuffer.wrap(bytes,28,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(992);
        bytes[32]=0x32; bytes[33]=0x54; bytes[34]=(byte)0xcd; bytes[35]=(byte)0xab;
        System.arraycopy(version.getBytes(StandardCharsets.US_ASCII),0,bytes,48,version.length());
        byte[] marker=("CARLED_HW:"+hardware+"\0").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker,0,bytes,400,marker.length);
        byte[] firmwareMarker=("CARLED_FW:"+version+"\0").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(firmwareMarker,0,bytes,450,firmwareMarker.length);
        int checksum=0xef; for(int i=32;i<1024;i++) checksum^=bytes[i]&255;
        bytes[1039]=(byte)checksum; return bytes;
    }
    @Test void rejectsInvalidImagesVersionsAndHardware() {
        assertThrows(FirmwareException.class,()->firmware.upload(new byte[1024],"a.bin","1.2.0","ESP32-V1"));
        assertThrows(FirmwareException.class,()->firmware.upload(image("1.2.0","ESP32-V1"),"a.bin","1.3.0","ESP32-V1"));
        assertThrows(FirmwareException.class,()->firmware.upload(image("1.2.0","ESP32-V1"),"a.bin","1.2.0","ESP32-V2"));
        assertThrows(FirmwareException.class,()->firmware.upload(image("1.2.0","ESP32-V1"),"a.txt","1.2.0","ESP32-V1"));
    }
    @Test void ownerCanDownloadAndRevocationStopsExistingSessions() throws Exception {
        var release=firmware.upload(image("1.2.0","ESP32-V1"),"firmware.bin","1.2.0","ESP32-V1");
        firmware.activate(release.id());
        firmware.provision(new FirmwareRequests.Device("AABBCCDDEEFF",1L,"ESP32-V1"));
        assertTrue(firmware.check("AABBCCDDEEFF",1L,"1.1.0").updateAvailable());
        assertFalse(firmware.check("AABBCCDDEEFF",1L,"1.2.0").updateAvailable());
        assertThrows(FirmwareException.class,()->firmware.begin("AABBCCDDEEFF",2L,new FirmwareRequests.Session("1.1.0","a".repeat(32))));
        var session=firmware.begin("AABBCCDDEEFF",1L,new FirmwareRequests.Session("1.1.0","a".repeat(32)));
        var manifest=firmware.manifest(session.token());
        assertEquals("AABBCCDDEEFF",manifest.deviceId()); assertEquals("a".repeat(32),manifest.nonce());
        assertEquals(FirmwarePolicy.sha256(image("1.2.0","ESP32-V1")),manifest.sha256());
        mvc.perform(get("/api/firmware/downloads/"+session.token()+"/manifest"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/firmware/downloads/"+session.token()+"/binary"))
            .andExpect(status().isOk()).andExpect(content().bytes(image("1.2.0","ESP32-V1")));
        firmware.deprecate(release.id());
        assertThrows(FirmwareException.class,()->firmware.manifest(session.token()));
        assertFalse(firmware.check("AABBCCDDEEFF",1L,"1.1.0").updateAvailable());
        assertEquals(1,firmware.list().size());
    }
    @Test void expiredSessionAndUnknownCapabilityAreDenied() {
        var release=firmware.upload(image("1.2.0","ESP32-V1"),"firmware.bin","1.2.0","ESP32-V1"); firmware.activate(release.id());
        firmware.provision(new FirmwareRequests.Device("AABBCCDDEEFF",1L,"ESP32-V1"));
        var session=firmware.begin("AABBCCDDEEFF",1L,new FirmwareRequests.Session("1.1.0","a".repeat(32)));
        var record=sessions.findAll().getFirst(); record.setExpiresAt(Instant.now().minusSeconds(1)); sessions.save(record);
        assertThrows(FirmwareException.class,()->firmware.binary(session.token()));
        assertThrows(FirmwareException.class,()->firmware.manifest("0".repeat(64)));
    }
    @Test void compatibleExplicitRollbackKeepsAllHistory() {
        var old=firmware.upload(image("1.1.0","ESP32-V1"),"old.bin","1.1.0","ESP32-V1");
        var newer=firmware.upload(image("1.2.0","ESP32-V1"),"new.bin","1.2.0","ESP32-V1");
        firmware.upload(image("1.3.0","ESP32-V2"),"other.bin","1.3.0","ESP32-V2");
        firmware.provision(new FirmwareRequests.Device("AABBCCDDEEFF",1L,"ESP32-V1"));
        firmware.activate(newer.id()); firmware.activate(old.id());
        assertEquals("1.1.0",firmware.check("AABBCCDDEEFF",1L,"1.2.0").release().version());
        assertEquals(3,firmware.list().size());
    }
    @Test void adminEndpointsRejectCustomerAndAnonymous() throws Exception {
        mvc.perform(get("/api/management/admin/firmware")).andExpect(status().isUnauthorized());
        String customer=jwt.createAccessToken(1L,"customer@example.com",List.of("ROLE_CUSTOMER"),java.util.Set.of(),"1");
        mvc.perform(get("/api/management/admin/firmware").header("Authorization","Bearer "+customer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        String admin=jwt.createAccessToken(1L,"admin@example.com",List.of("ROLE_ADMIN"),java.util.Set.of(),"1");
        mvc.perform(get("/api/management/admin/firmware").header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }
    @Test void builtEsp32ApplicationPassesUploadPolicy() throws Exception {
        String file=System.getProperty("firmwareImage");
        org.junit.jupiter.api.Assumptions.assumeTrue(file!=null,"Set -DfirmwareImage to validate a real PlatformIO build");
        byte[] bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(file));
        assertEquals(FirmwarePolicy.sha256(bytes),FirmwarePolicy.validate(bytes,"1.1.0","ESP32-V1"));
    }
}
