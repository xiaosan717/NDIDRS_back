package com.dorm.ndidrs_back.controller;

import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.mapper.DormRoomMapper;
import com.dorm.ndidrs_back.service.EmailService;
import com.dorm.ndidrs_back.service.SysUserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class LoginController {
    private final SysUserService sysUserService;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final DormRoomMapper dormRoomMapper;
    private final Map<String, String> codeCache = new ConcurrentHashMap<>();
    private final Map<String, Long> codeTimeCache = new ConcurrentHashMap<>();

    public LoginController(SysUserService sysUserService, JwtUtils jwtUtils,
                           EmailService emailService, DormRoomMapper dormRoomMapper) {
        this.sysUserService = sysUserService;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
        this.dormRoomMapper = dormRoomMapper;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        String expectedRole = loginData.get("expectedRole");
        
        SysUser user = sysUserService.login(username, password);
        if (user != null) {
            // 验证角色是否匹配
            if (expectedRole != null && !expectedRole.isEmpty()) {
                if (!expectedRole.equals(user.getRole())) {
                    String errorMsg = "ADMIN".equals(expectedRole) ? "该账号不是管理员账号" :
                                     "COUNSELOR".equals(expectedRole) ? "该账号不是辅导员账号" :
                                     "DORM_MANAGER".equals(expectedRole) ? "该账号不是宿管账号" : "角色不匹配";
                    return Result.error(403, errorMsg);
                }
            }
            // 学生入口只允许学生和宿舍长登录
            if (expectedRole == null || expectedRole.isEmpty()) {
                if (!"STUDENT".equals(user.getRole()) && !"DORM_LEADER".equals(user.getRole())) {
                    return Result.error(403, "该账号不是学生账号，请选择正确的登录入口");
                }
            }
            
            String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("user", user);
            return Result.success(result);
        }
        return Result.error(401, "用户名或密码错误");
    }

    @PostMapping("/sendCode")
    public Result<Void> sendCode(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        if (email == null || email.isEmpty()) {
            return Result.error(400, "邮箱不能为空");
        }
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return Result.error(400, "邮箱格式不正确");
        }
        Long lastSendTime = codeTimeCache.get(email);
        if (lastSendTime != null && System.currentTimeMillis() - lastSendTime < 60000) {
            return Result.error(400, "验证码发送过于频繁，请1分钟后再试");
        }
        String code = String.format("%06d", new Random().nextInt(999999));
        codeCache.put(email, code);
        codeTimeCache.put(email, System.currentTimeMillis());
        try {
            emailService.sendVerificationCode(email, code);
            return Result.success("验证码已发送", null);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(500, "验证码发送失败：" + e.getMessage());
        }
    }

    @PostMapping("/register/step1")
    public Result<Map<String, Object>> registerStep1(@RequestBody Map<String, Object> data) {
        String username = (String) data.get("username");
        String password = (String) data.get("password");
        String email = (String) data.get("email");
        String code = (String) data.get("code");

        if (username == null || username.isEmpty()) return Result.error(400, "用户名不能为空");
        if (password == null || password.isEmpty()) return Result.error(400, "密码不能为空");
        if (email == null || email.isEmpty()) return Result.error(400, "邮箱不能为空");
        if (code == null || code.isEmpty()) return Result.error(400, "验证码不能为空");

        String cachedCode = codeCache.get(email);
        Long sendTime = codeTimeCache.get(email);
        if (cachedCode == null) {
            return Result.error(400, "请先获取验证码");
        }
        if (sendTime != null && System.currentTimeMillis() - sendTime > 300000) {
            codeCache.remove(email);
            codeTimeCache.remove(email);
            return Result.error(400, "验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(code)) {
            return Result.error(400, "验证码错误");
        }

        if (sysUserService.existsByUsername(username)) {
            return Result.error(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setRole("STUDENT");
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        sysUserService.save(user);

        codeCache.remove(email);
        codeTimeCache.remove(email);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        return Result.success("第一步完成", result);
    }

    @PostMapping("/register/step2")
    @Transactional
    public Result<Void> registerStep2(@RequestBody Map<String, Object> data) {
        Long userId = ((Number) data.get("userId")).longValue();
        String realName = (String) data.get("realName");
        String phone = (String) data.get("phone");
        String college = (String) data.get("college");
        String className = (String) data.get("className");
        String grade = (String) data.get("grade");
        String building = (String) data.get("building");
        String room = (String) data.get("room");
        boolean isDormLeader = Boolean.TRUE.equals(data.get("isDormLeader"));

        if (userId == null) return Result.error(400, "用户ID不能为空");
        if (realName == null || realName.isEmpty()) return Result.error(400, "真实姓名不能为空");
        if (phone == null || phone.isEmpty()) return Result.error(400, "手机号不能为空");
        if (college == null || college.isEmpty()) return Result.error(400, "学院不能为空");
        if (className == null || className.isEmpty()) return Result.error(400, "班级不能为空");
        if (grade == null || grade.isEmpty()) return Result.error(400, "年级不能为空");
        if (building == null || building.isEmpty()) return Result.error(400, "楼栋不能为空");
        if (room == null || room.isEmpty()) return Result.error(400, "宿舍号不能为空");

        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error(400, "用户不存在");
        }
        if (user.getStatus() != 0) {
            return Result.error(400, "用户已完成注册");
        }

        DormRoom dormRoom = null;
        if (isDormLeader) {
            dormRoom = dormRoomMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DormRoom>()
                            .eq(DormRoom::getBuilding, building)
                            .eq(DormRoom::getRoomNumber, room)
            );
            if (dormRoom == null) {
                return Result.error(400, "所选宿舍不存在");
            }
            if (dormRoom.getLeaderId() != null) {
                return Result.error(400, "该宿舍已有宿舍长，无法注册为宿舍长");
            }
        }

        user.setRealName(realName);
        user.setPhone(phone);
        user.setCollege(college);
        user.setClassName(className);
        user.setGrade(grade);
        user.setBuilding(building);
        user.setRoom(room);
        user.setRole(isDormLeader ? "DORM_LEADER" : "STUDENT");
        user.setStatus(1);
        user.setUpdateTime(LocalDateTime.now());

        sysUserService.updateById(user);

        if (isDormLeader && dormRoom != null) {
            dormRoom.setLeaderId(user.getId());
            dormRoomMapper.updateById(dormRoom);
        }

        return Result.success("注册成功", null);
    }

    @PostMapping("/register")
    @Transactional
    public Result<Void> register(@RequestBody Map<String, Object> data) {
        String username = (String) data.get("username");
        String password = (String) data.get("password");
        String email = (String) data.get("email");
        String code = (String) data.get("code");
        String realName = (String) data.get("realName");
        String phone = (String) data.get("phone");
        String college = (String) data.get("college");
        String className = (String) data.get("className");
        String grade = (String) data.get("grade");
        String building = (String) data.get("building");
        String room = (String) data.get("room");
        boolean isDormLeader = Boolean.TRUE.equals(data.get("isDormLeader"));

        if (username == null || username.isEmpty()) return Result.error(400, "用户名不能为空");
        if (password == null || password.isEmpty()) return Result.error(400, "密码不能为空");
        if (email == null || email.isEmpty()) return Result.error(400, "邮箱不能为空");
        if (code == null || code.isEmpty()) return Result.error(400, "验证码不能为空");
        if (realName == null || realName.isEmpty()) return Result.error(400, "真实姓名不能为空");
        if (phone == null || phone.isEmpty()) return Result.error(400, "手机号不能为空");
        if (college == null || college.isEmpty()) return Result.error(400, "学院不能为空");
        if (className == null || className.isEmpty()) return Result.error(400, "班级不能为空");
        if (grade == null || grade.isEmpty()) return Result.error(400, "年级不能为空");
        if (building == null || building.isEmpty()) return Result.error(400, "楼栋不能为空");
        if (room == null || room.isEmpty()) return Result.error(400, "宿舍号不能为空");

        String cachedCode = codeCache.get(email);
        Long sendTime = codeTimeCache.get(email);
        if (cachedCode == null) return Result.error(400, "请先获取验证码");
        if (sendTime != null && System.currentTimeMillis() - sendTime > 300000) {
            codeCache.remove(email);
            codeTimeCache.remove(email);
            return Result.error(400, "验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(code)) return Result.error(400, "验证码错误");

        if (sysUserService.existsByUsername(username)) return Result.error(400, "用户名已存在");

        DormRoom dormRoom = null;
        if (isDormLeader) {
            dormRoom = dormRoomMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DormRoom>()
                            .eq(DormRoom::getBuilding, building)
                            .eq(DormRoom::getRoomNumber, room)
            );
            if (dormRoom == null) return Result.error(400, "所选宿舍不存在");
            if (dormRoom.getLeaderId() != null) return Result.error(400, "该宿舍已有宿舍长，无法注册为宿舍长");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setRealName(realName);
        user.setPhone(phone);
        user.setCollege(college);
        user.setClassName(className);
        user.setGrade(grade);
        user.setBuilding(building);
        user.setRoom(room);
        user.setRole(isDormLeader ? "DORM_LEADER" : "STUDENT");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        sysUserService.save(user);

        if (isDormLeader && dormRoom != null) {
            dormRoom.setLeaderId(user.getId());
            dormRoomMapper.updateById(dormRoom);
        }

        codeCache.remove(email);
        codeTimeCache.remove(email);

        return Result.success("注册成功", null);
    }
}
