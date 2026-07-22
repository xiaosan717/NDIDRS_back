package com.dorm.ndidrs_back.controller;

import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.dto.AiAnalysisRequest;
import com.dorm.ndidrs_back.dto.AiAnalysisResponse;
import com.dorm.ndidrs_back.service.AiAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {
    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "COUNSELOR", "DORM_MANAGER");
    private static final long MIN_INTERVAL_MILLIS = 10_000L;

    private final AiAnalysisService aiAnalysisService;
    private final JwtUtils jwtUtils;
    private final Map<Long, Long> lastRequestTimes = new ConcurrentHashMap<>();

    public AiAnalysisController(AiAnalysisService aiAnalysisService, JwtUtils jwtUtils) {
        this.aiAnalysisService = aiAnalysisService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/analysis")
    public Result<AiAnalysisResponse> analyze(@RequestBody(required = false) AiAnalysisRequest body,
                                             HttpServletRequest request) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null || role == null) {
            return Result.error(401, "登录状态已失效，请重新登录");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return Result.error(403, "当前角色没有 AI 分析权限");
        }

        long now = System.currentTimeMillis();
        Long previous = lastRequestTimes.put(userId, now);
        if (previous != null && now - previous < MIN_INTERVAL_MILLIS) {
            lastRequestTimes.put(userId, previous);
            return Result.error(429, "请求过于频繁，请稍后再试");
        }

        try {
            return Result.success(aiAnalysisService.analyze(userId, role, body));
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
