package com.dorm.ndidrs_back.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;

@Service
public class TrtcUserSigService {
    private static final long DEFAULT_EXPIRE_SECONDS = 7 * 24 * 60 * 60;

    private final ObjectMapper objectMapper;

    @Value("${trtc.sdk-app-id}")
    private long sdkAppId;

    @Value("${trtc.sdk-secret-key}")
    private String sdkSecretKey;

    public TrtcUserSigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public long getSdkAppId() {
        return sdkAppId;
    }

    public long getExpireSeconds() {
        return DEFAULT_EXPIRE_SECONDS;
    }

    public String generate(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("TRTC 用户标识不能为空");
        }
        if (sdkAppId <= 0 || sdkSecretKey == null || sdkSecretKey.isBlank()) {
            throw new IllegalStateException("TRTC 服务尚未配置");
        }

        try {
            long currentTime = Instant.now().getEpochSecond();
            String contentToSign = "TLS.identifier:" + userId + "\n"
                    + "TLS.sdkappid:" + sdkAppId + "\n"
                    + "TLS.time:" + currentTime + "\n"
                    + "TLS.expire:" + DEFAULT_EXPIRE_SECONDS + "\n";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sdkSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(contentToSign.getBytes(StandardCharsets.UTF_8)));

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("TLS.ver", "2.0");
            document.put("TLS.identifier", userId);
            document.put("TLS.sdkappid", sdkAppId);
            document.put("TLS.expire", DEFAULT_EXPIRE_SECONDS);
            document.put("TLS.time", currentTime);
            document.put("TLS.sig", signature);

            byte[] compressed = deflate(objectMapper.writeValueAsBytes(document));
            return Base64.getEncoder().encodeToString(compressed)
                    .replace('+', '*')
                    .replace('/', '-')
                    .replace('=', '_');
        } catch (Exception e) {
            throw new IllegalStateException("生成 TRTC UserSig 失败", e);
        }
    }

    private byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream(input.length)) {
            byte[] buffer = new byte[512];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("压缩 TRTC UserSig 失败", e);
        } finally {
            deflater.end();
        }
    }
}
