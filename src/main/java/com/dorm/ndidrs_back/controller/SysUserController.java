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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                                       @RequestParam(required = false) String building) {
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
        } else {
            if (role != null) wrapper.eq(SysUser::getRole, role);
            if (building != null) wrapper.eq(SysUser::getBuilding, building);
        }
        Page<SysUser> page = sysUserService.page(new Page<>(pageNum, pageSize), wrapper);
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