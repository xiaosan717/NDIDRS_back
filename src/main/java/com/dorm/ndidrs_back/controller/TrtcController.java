package com.dorm.ndidrs_back.controller;

import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.SysUserService;
import com.dorm.ndidrs_back.service.TrtcUserSigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/trtc")
public class TrtcController {
    private final JwtUtils jwtUtils;
    private final SysUserService userService;
    private final TrtcUserSigService userSigService;
    private final HttpServletRequest request;

    // 内存存储：roomId -> 会议密码（空密码代表无需密码）
    private static final ConcurrentHashMap<String, String> ROOM_PASSWORD_STORE = new ConcurrentHashMap<>();

    // 内存存储：roomId -> 会议范围元数据（创建人、类型、班级/宿舍范围）
    private static final ConcurrentHashMap<String, RoomMeta> ROOM_META = new ConcurrentHashMap<>();

    // 会议号格式校验：xxx-xxx-xxx 纯数字（如 123-456-789）
    private static final java.util.regex.Pattern ROOM_ID_PATTERN =
            java.util.regex.Pattern.compile("^\\d{3}-\\d{3}-\\d{3}$");

    private static class RoomMeta {
        String ownerRole;          // 创建人角色 COUNSELOR / DORM_LEADER / ADMIN / DORM_MANAGER
        Long ownerUserId;
        // 班级会议（辅导员创建）
        String college;
        String grade;
        String className;
        // 宿舍会议（寝室长创建）
        String building;
        String room;
    }

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
    public Result<Map<String, Object>> canJoin(@RequestParam String roomId,
                                               @RequestParam(required = false) String password) {
        if (roomId == null || roomId.isBlank()) {
            return Result.error(400, "会议号不能为空");
        }
        String rid = roomId.trim();
        if (!ROOM_ID_PATTERN.matcher(rid).matches()) {
            return Result.error(400, "会议号格式不正确，应为 123-456-789 格式");
        }
        String pwd = password == null ? "" : password;

        SysUser user = currentUser();
        boolean allowed = canJoinRoom(user, rid);
        if (!allowed) {
            return Result.error(403, "当前账号不在该会议的允许范围内");
        }

        // 密码校验：如果该会议设置了密码，则必须匹配
        String storedPwd = ROOM_PASSWORD_STORE.get(rid);
        if (storedPwd != null && !storedPwd.isEmpty()) {
            if (!storedPwd.equals(pwd)) {
                return Result.error(401, "会议密码不正确");
            }
        }

        Map<String, Object> data = userScope(user);
        data.put("canJoin", true);
        data.put("roomId", rid);
        data.put("passwordRequired", storedPwd != null && !storedPwd.isEmpty());
        return Result.success(data);
    }

    @GetMapping("/roomInfo")
    public Result<Map<String, Object>> roomInfo(@RequestParam String roomId) {
        String rid = roomId == null ? "" : roomId.trim();
        String storedPwd = ROOM_PASSWORD_STORE.get(rid);
        RoomMeta meta = ROOM_META.get(rid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", rid);
        data.put("passwordRequired", storedPwd != null && !storedPwd.isEmpty());
        data.put("exists", storedPwd != null || meta != null);
        data.put("validFormat", ROOM_ID_PATTERN.matcher(rid).matches());
        return Result.success(data);
    }

    @PostMapping("/registerRoom")
    public Result<Map<String, Object>> registerRoom(@RequestBody Map<String, String> body) {
        String roomId = body.get("roomId");
        String password = body.get("password");
        if (roomId == null || roomId.isBlank()) {
            return Result.error(400, "会议号不能为空");
        }
        String rid = roomId.trim();
        if (!ROOM_ID_PATTERN.matcher(rid).matches()) {
            return Result.error(400, "会议号格式不正确，应为 123-456-789 格式");
        }
        String pwd = password == null ? "" : password;

        SysUser user = currentUser();
        // 只有有发起权限的角色可以创建会议（辅导员/寝室长；管理员/宿管也能发起）
        boolean counselor = "COUNSELOR".equals(user.getRole())
                && hasText(user.getCollege()) && hasText(user.getGrade()) && hasText(user.getClassName());
        boolean dormLeader = "DORM_LEADER".equals(user.getRole())
                && hasText(user.getBuilding()) && hasText(user.getRoom());
        boolean admin = "ADMIN".equals(user.getRole()) || "DORM_MANAGER".equals(user.getRole());

        if (!admin && !counselor && !dormLeader) {
            return Result.error(403, "当前账号无权限发起会议");
        }

        // 记录会议范围元数据（决定谁能加入）
        RoomMeta meta = new RoomMeta();
        meta.ownerRole = user.getRole();
        meta.ownerUserId = user.getId();
        if (counselor) {
            // 辅导员创建的是班级会议：只有本学院本年级本班级可加入
            meta.college = user.getCollege();
            meta.grade = user.getGrade();
            meta.className = user.getClassName();
        } else if (dormLeader) {
            // 寝室长创建的是宿舍会议：只有本楼栋本宿舍可加入
            meta.building = user.getBuilding();
            meta.room = user.getRoom();
        } else {
            // ADMIN / DORM_MANAGER 创建的是全员会议：通过 ROOM_META 存在但字段为空表示不限范围
            meta.college = null;
            meta.grade = null;
            meta.className = null;
            meta.building = null;
            meta.room = null;
        }
        ROOM_META.put(rid, meta);
        ROOM_PASSWORD_STORE.put(rid, pwd);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", rid);
        data.put("passwordRequired", !pwd.isEmpty());
        data.put("ownerRole", meta.ownerRole);
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
        String role = user.getRole();
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        String rid = roomId.trim();

        // 1. 管理员和宿管：可以加入任意会议
        if ("ADMIN".equals(role) || "DORM_MANAGER".equals(role)) {
            return true;
        }

        // 2. 会议必须已经被发起人注册（未注册的会议号禁止加入）
        RoomMeta meta = ROOM_META.get(rid);
        if (meta == null) {
            // 还没被创建的会议号，只有有发起权限的人可以作为"第一个人"自动隐式创建（实际会在前端发起时registerRoom，这里默认拒绝未注册的号）
            return false;
        }

        // 3. 会议创建人本身：直接允许
        if (meta.ownerUserId != null && meta.ownerUserId.equals(user.getId())) {
            return true;
        }

        // 4. 班级会议（辅导员创建的）：只有本学院同年级同班级可加入
        boolean isClassMeeting = hasText(meta.college) && hasText(meta.grade) && hasText(meta.className);
        if (isClassMeeting) {
            boolean userInClass = hasText(user.getCollege()) && hasText(user.getGrade()) && hasText(user.getClassName())
                    && meta.college.equals(user.getCollege())
                    && meta.grade.equals(user.getGrade())
                    && meta.className.equals(user.getClassName());
            if (!userInClass) {
                // 辅导员自己创建的会议，允许辅导员（或其他同范围辅导员）进入也已通过 creator 判断了
                return false;
            }
            // 会议创建人是辅导员：学生/寝室长/辅导员都在班级内 -> 可进
            return true;
        }

        // 5. 宿舍会议（寝室长创建的）：只有同楼栋同宿舍可加入
        boolean isDormMeeting = hasText(meta.building) && hasText(meta.room);
        if (isDormMeeting) {
            boolean userInDorm = hasText(user.getBuilding()) && hasText(user.getRoom())
                    && meta.building.equals(user.getBuilding())
                    && meta.room.equals(user.getRoom());
            if (!userInDorm) {
                // 管理员/宿管已经在上面通过；其余角色必须在同宿舍
                return false;
            }
            return true;
        }

        // 6. ADMIN / DORM_MANAGER 创建的无限制会议：所有已登录用户可进（但管理员/宿管已经提前返回，这里只处理普通用户）
        boolean isOpenMeeting = !isClassMeeting && !isDormMeeting;
        if (isOpenMeeting) {
            return true;
        }

        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
