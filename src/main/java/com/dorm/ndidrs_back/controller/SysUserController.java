package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class SysUserController {
    private final SysUserService sysUserService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequest request;

    public SysUserController(SysUserService sysUserService, JwtUtils jwtUtils, HttpServletRequest request) {
        this.sysUserService = sysUserService;
        this.jwtUtils = jwtUtils;
        this.request = request;
    }

    @GetMapping
    public Result<Page<SysUser>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) String building,
                                       @RequestParam(required = false) String className,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        String currentRole = jwtUtils.getCurrentUserRole(request);
        SysUser currentUser = sysUserService.getById(jwtUtils.getCurrentUserId(request));

        if ("COUNSELOR".equals(currentRole) && currentUser != null) {
            wrapper.eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                wrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            wrapper.eq(SysUser::getClassName, currentUser.getClassName())
                   .in(SysUser::getRole, Arrays.asList("STUDENT", "DORM_LEADER"));
        } else if ("DORM_MANAGER".equals(currentRole) && currentUser != null) {
            wrapper.eq(SysUser::getBuilding, currentUser.getBuilding())
                   .in(SysUser::getRole, Arrays.asList("STUDENT", "DORM_LEADER"));
        } else if (!"ADMIN".equals(currentRole)) {
            if (currentUser == null) {
                return Result.error(401, "请先登录");
            }
            wrapper.eq(SysUser::getId, currentUser.getId());
        }

        if (hasText(role)) wrapper.eq(SysUser::getRole, role.trim());
        if (hasText(building)) wrapper.eq(SysUser::getBuilding, building.trim());
        if (hasText(className)) wrapper.eq(SysUser::getClassName, className.trim());
        if (status != null) wrapper.eq(SysUser::getStatus, status);
        if (hasText(keyword)) {
            String searchText = keyword.trim();
            wrapper.and(query -> query
                    .like(SysUser::getUsername, searchText)
                    .or().like(SysUser::getRealName, searchText)
                    .or().like(SysUser::getEmail, searchText)
                    .or().like(SysUser::getPhone, searchText)
                    .or().like(SysUser::getCollege, searchText)
                    .or().like(SysUser::getClassName, searchText)
                    .or().like(SysUser::getGrade, searchText)
                    .or().like(SysUser::getBuilding, searchText)
                    .or().like(SysUser::getRoom, searchText));
        }
        wrapper.orderByDesc(SysUser::getId);

        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null ? 10 : Math.min(Math.max(pageSize, 1), 100);
        Page<SysUser> page = sysUserService.page(new Page<>(safePageNum, safePageSize), wrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        sysUserService.save(user);
        return Result.success("添加成功", null);
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysUser user) {
        sysUserService.updateById(user);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/byRole/{role}")
    public Result<List<SysUser>> getByRole(@PathVariable String role) {
        List<SysUser> users = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, role)
                .eq(SysUser::getStatus, 1));
        return Result.success(users);
    }

    @GetMapping("/byBuilding/{building}")
    public Result<List<SysUser>> getByBuilding(@PathVariable String building) {
        List<SysUser> users = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getBuilding, building)
                .eq(SysUser::getStatus, 1));
        return Result.success(users);
    }

    @GetMapping("/byClass/{className}")
    public Result<List<SysUser>> getByClass(@PathVariable String className) {
        List<SysUser> users = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getClassName, className)
                .eq(SysUser::getStatus, 1));
        return Result.success(users);
    }

    @GetMapping("/byRoom")
    public Result<List<SysUser>> getByRoom(@RequestParam String building, @RequestParam String room) {
        List<SysUser> users = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getBuilding, building)
                .eq(SysUser::getRoom, room)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getRole));
        return Result.success(users);
    }

    @PostMapping("/{id}/password")
    public Result<Void> changePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if (!user.getPassword().equals(oldPassword)) {
            return Result.error(400, "原密码错误");
        }
        user.setPassword(newPassword);
        sysUserService.updateById(user);
        return Result.success("密码修改成功", null);
    }

    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest body) {
        if (body == null || !hasText(body.getNewPassword())) {
            return Result.error(400, "新密码不能为空");
        }
        String newPassword = body.getNewPassword().trim();
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            return Result.error(400, "新密码长度应为6至64位");
        }

        SysUser target = sysUserService.getById(id);
        if (target == null) {
            return Result.error(404, "用户不存在");
        }
        if (!canManage(target)) {
            return Result.error(403, "无权重置该用户的密码");
        }

        target.setPassword(newPassword);
        target.setUpdateTime(LocalDateTime.now());
        sysUserService.updateById(target);
        return Result.success("密码重置成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        if (body == null || !isValidStatus(body.getStatus())) {
            return Result.error(400, "账号状态只能为启用或停用");
        }
        return updateUsersStatus(List.of(id), body.getStatus());
    }

    @PutMapping("/batch-status")
    public Result<Void> updateBatchStatus(@RequestBody BatchStatusRequest body) {
        if (body == null || body.getIds() == null || body.getIds().isEmpty()) {
            return Result.error(400, "请至少选择一个用户");
        }
        if (!isValidStatus(body.getStatus())) {
            return Result.error(400, "账号状态只能为启用或停用");
        }
        return updateUsersStatus(body.getIds(), body.getStatus());
    }

    private Result<Void> updateUsersStatus(List<Long> ids, Integer status) {
        Set<Long> distinctIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Result.error(400, "请选择有效的用户");
        }

        List<SysUser> targets = new ArrayList<>(sysUserService.listByIds(distinctIds));
        if (targets.size() != distinctIds.size()) {
            return Result.error(404, "部分用户不存在，请刷新列表后重试");
        }
        if (status == 0 && distinctIds.contains(jwtUtils.getCurrentUserId(request))) {
            return Result.error(400, "不能停用当前登录账号");
        }
        if (targets.stream().anyMatch(target -> !canManage(target))) {
            return Result.error(403, "所选用户中包含无权操作的账号");
        }

        LocalDateTime now = LocalDateTime.now();
        targets.forEach(target -> {
            target.setStatus(status);
            target.setUpdateTime(now);
        });
        sysUserService.updateBatchById(targets);
        return Result.success(status == 1 ? "账号启用成功" : "账号停用成功", null);
    }

    private boolean canManage(SysUser target) {
        String currentRole = jwtUtils.getCurrentUserRole(request);
        if ("ADMIN".equals(currentRole)) {
            return true;
        }
        if (!"DORM_MANAGER".equals(currentRole)) {
            return false;
        }
        SysUser currentUser = sysUserService.getById(jwtUtils.getCurrentUserId(request));
        return currentUser != null
                && Objects.equals(currentUser.getBuilding(), target.getBuilding())
                && Arrays.asList("STUDENT", "DORM_LEADER").contains(target.getRole());
    }

    private boolean isValidStatus(Integer status) {
        return status != null && (status == 0 || status == 1);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class ResetPasswordRequest {
        private String newPassword;

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class StatusRequest {
        private Integer status;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class BatchStatusRequest extends StatusRequest {
        private List<Long> ids;

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
    }

    @PostMapping("/{id}/avatar")
    public Result<Map<String, String>> uploadAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择要上传的图片");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif)$")) {
            return Result.error(400, "只支持jpg/jpeg/png/gif格式的图片");
        }
        
        String uploadDir = System.getProperty("user.dir") + "/uploads/avatars";
        File dir = new File(uploadDir);
        System.out.println("上传目录: " + uploadDir);
        System.out.println("目录是否存在: " + dir.exists());
        
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("目录创建结果: " + created);
        }
        
        String newFilename = UUID.randomUUID().toString() + originalFilename.substring(originalFilename.lastIndexOf("."));
        String filePath = uploadDir + "/" + newFilename;
        System.out.println("文件路径: " + filePath);
        
        try {
            file.transferTo(new File(filePath));
            String avatarUrl = "/api/users/avatar/" + newFilename;
            SysUser user = sysUserService.getById(id);
            if (user != null) {
                if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                    String oldAvatarPath = System.getProperty("user.dir") + "/uploads/avatars/" + user.getAvatar().substring(user.getAvatar().lastIndexOf("/") + 1);
                    new File(oldAvatarPath).delete();
                }
                user.setAvatar(avatarUrl);
                sysUserService.updateById(user);
            }
            Map<String, String> result = Map.of("url", avatarUrl);
            return Result.success(result);
        } catch (IOException e) {
            e.printStackTrace();
            return Result.error(500, "图片上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/avatar/{filename}")
    public void getAvatar(@PathVariable String filename, jakarta.servlet.http.HttpServletResponse response) {
        String filePath = System.getProperty("user.dir") + "/uploads/avatars/" + filename;
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                String contentType = Files.probeContentType(path);
                response.setContentType(contentType);
                Files.copy(path, response.getOutputStream());
            } else {
                response.setStatus(404);
            }
        } catch (IOException e) {
            response.setStatus(500);
        }
    }
}
