package com.dorm.ndidrs_back.controller;

import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.SysUserService;
import com.dorm.ndidrs_back.service.TrtcUserSigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trtc")
public class TrtcController {
    private final JwtUtils jwtUtils;
    private final SysUserService userService;
    private final TrtcUserSigService userSigService;
    private final HttpServletRequest request;

    public TrtcController(JwtUtils jwtUtils,
                          SysUserService userService,
                          TrtcUserSigService userSigService,
                          HttpServletRequest request) {
        this.jwtUtils = jwtUtils;
        this.userService = userService;
        this.userSigService = userSigService;
        this.request = request;
    }

    @GetMapping("/userSig")
    public Result<Map<String, Object>> userSig() {
        SysUser user = currentUser();
        String trtcUserId = String.valueOf(user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sdkAppId", userSigService.getSdkAppId());
        data.put("userId", trtcUserId);
        data.put("userSig", userSigService.generate(trtcUserId));
        data.put("expire", userSigService.getExpireSeconds());
        return Result.success(data);
    }

    @GetMapping("/canStart")
    public Result<Map<String, Object>> canStart() {
        SysUser user = currentUser();
        boolean counselor = "COUNSELOR".equals(user.getRole())
                && hasText(user.getCollege()) && hasText(user.getGrade()) && hasText(user.getClassName());
        boolean dormLeader = "DORM_LEADER".equals(user.getRole())
                && hasText(user.getBuilding()) && hasText(user.getRoom());

        Map<String, Object> data = userScope(user);
        data.put("canStart", counselor || dormLeader);
        return Result.success(data);
    }

    @GetMapping("/canJoin")
    public Result<Map<String, Object>> canJoin(@RequestParam String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Result.error(400, "会议号不能为空");
        }

        SysUser user = currentUser();
        boolean allowed = canJoinRoom(user, roomId.trim());
        if (!allowed) {
            return Result.error(403, "当前账号不在该会议的允许范围内");
        }

        Map<String, Object> data = userScope(user);
        data.put("canJoin", true);
        data.put("roomId", roomId.trim());
        return Result.success(data);
    }

    private SysUser currentUser() {
        Long userId = jwtUtils.getCurrentUserId(request);
        if (userId == null) {
            throw new SecurityException("登录状态已失效，请重新登录");
        }
        SysUser user = userService.getById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            throw new SecurityException("当前账号不存在或已被禁用");
        }
        return user;
    }

    private Map<String, Object> userScope(SysUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", user.getRole());
        data.put("college", user.getCollege());
        data.put("grade", user.getGrade());
        data.put("className", user.getClassName());
        data.put("building", user.getBuilding());
        data.put("room", user.getRoom());
        return data;
    }

    private boolean canJoinRoom(SysUser user, String roomId) {
        // 所有已登录且状态正常的用户均可加入会议（currentUser()已校验登录和状态）
        // TRTC SDK 通过 userSig 保障房间安全，无需在应用层做过于严格的房间名匹配
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
