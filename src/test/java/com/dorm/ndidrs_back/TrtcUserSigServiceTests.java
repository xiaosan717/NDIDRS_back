package com.dorm.ndidrs_back;

import com.dorm.ndidrs_back.service.TrtcUserSigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TrtcUserSigServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatedUserSigContainsValidHmacDocument() throws Exception {
        long sdkAppId = 1_400_000_000L;
        String secretKey = "test-secret-key";
        String userId = "12345";

        TrtcUserSigService service = new TrtcUserSigService(objectMapper);
        ReflectionTestUtils.setField(service, "sdkAppId", sdkAppId);
        ReflectionTestUtils.setField(service, "sdkSecretKey", secretKey);

        String userSig = service.generate(userId);
        byte[] compressed = Base64.getDecoder().decode(userSig
                .replace('*', '+').replace('-', '/').replace('_', '='));
        byte[] json;
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            json = input.readAllBytes();
        }
        Map<String, Object> document = objectMapper.readValue(json, new TypeReference<>() {});

        assertEquals("2.0", document.get("TLS.ver"));
        assertEquals(userId, document.get("TLS.identifier"));
        assertEquals(sdkAppId, ((Number) document.get("TLS.sdkappid")).longValue());
        assertFalse(String.valueOf(document.get("TLS.sig")).isBlank());

        long time = ((Number) document.get("TLS.time")).longValue();
        long expire = ((Number) document.get("TLS.expire")).longValue();
        String contentToSign = "TLS.identifier:" + userId + "\n"
                + "TLS.sdkappid:" + sdkAppId + "\n"
                + "TLS.time:" + time + "\n"
                + "TLS.expire:" + expire + "\n";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(
                mac.doFinal(contentToSign.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, document.get("TLS.sig"));
    }
}
