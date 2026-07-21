package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormLeave;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.DormLeaveService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaves")
public class DormLeaveController {
    private static final long MAX_PROOF_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Path PROOF_IMAGE_DIRECTORY = Path.of(
            System.getProperty("user.dir"), "uploads", "leaves").toAbsolutePath().normalize();

    private final DormLeaveService dormLeaveService;
    private final SysUserService userService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequest request;

    public DormLeaveController(DormLeaveService dormLeaveService, SysUserService userService,
                               JwtUtils jwtUtils, HttpServletRequest request) {
        this.dormLeaveService = dormLeaveService;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.request = request;
    }

    private void applyRoleFilter(LambdaQueryWrapper<DormLeave> wrapper) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null) return;

        SysUser currentUser = userService.getById(userId);
        if (currentUser == null) return;

        if ("STUDENT".equals(role) || "DORM_LEADER".equals(role)) {
            wrapper.eq(DormLeave::getStudentId, userId);
        } else if ("COUNSELOR".equals(role)) {
            List<Long> studentIds = userService.list(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getCollege, currentUser.getCollege())
                    .eq(SysUser::getGrade, currentUser.getGrade())
                    .eq(SysUser::getClassName, currentUser.getClassName())
                    .in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER")))
                    .stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormLeave::getStudentId, studentIds);
            } else {
                wrapper.eq(DormLeave::getId, -1L);
            }
        } else if ("DORM_MANAGER".equals(role)) {
            wrapper.eq(DormLeave::getId, -1L);
        }
    }

    @GetMapping
    public Result<Page<DormLeave>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) Long studentId,
                                         @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DormLeave> wrapper = new LambdaQueryWrapper<>();
        String role = jwtUtils.getCurrentUserRole(request);
        applyRoleFilter(wrapper);
        if (studentId != null) wrapper.eq(DormLeave::getStudentId, studentId);

        if ("ADMIN".equals(role)) {
            // 管理员永远看不到 PENDING（待辅导员审批）
            if (status != null) {
                wrapper.eq(DormLeave::getStatus, status);
                if ("PENDING".equals(status)) {
                    return Result.success(new Page<>(pageNum, pageSize));
                }
            } else {
                wrapper.in(DormLeave::getStatus, java.util.Arrays.asList("COUNSELOR_APPROVED", "APPROVED", "REJECTED"));
            }
        } else {
            if (status != null) wrapper.eq(DormLeave::getStatus, status);
        }
        wrapper.orderByDesc(DormLeave::getCreateTime);
        Page<DormLeave> page = dormLeaveService.page(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<DormLeave> getById(@PathVariable Long id) {
        return Result.success(dormLeaveService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody DormLeave leave) {
        String storedImage = null;
        if (leave.getProofImage() != null && leave.getProofImage().startsWith("data:image/")) {
            storedImage = storeProofImage(leave.getProofImage());
            leave.setProofImage(storedImage);
        }
        leave.setStatus("PENDING");
        try {
            if (!dormLeaveService.save(leave)) {
                throw new IllegalStateException("保存请假申请失败");
            }
        } catch (RuntimeException e) {
            deleteStoredImage(storedImage);
            throw e;
        }
        return Result.success("申请提交成功", null);
    }

    @GetMapping("/proof/{filename:.+}")
    public ResponseEntity<Resource> proofImage(@PathVariable String filename) throws IOException {
        if (!filename.matches("[A-Za-z0-9._-]+")) {
            return ResponseEntity.badRequest().build();
        }
        Path imagePath = PROOF_IMAGE_DIRECTORY.resolve(filename).normalize();
        if (!imagePath.startsWith(PROOF_IMAGE_DIRECTORY) || !Files.isRegularFile(imagePath)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(imagePath);
        MediaType mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(imagePath));
    }

    @PutMapping("/approve/{id}")
    public Result<Void> approve(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        DormLeave leave = dormLeaveService.getById(id);
        if (leave == null) {
            return Result.error(404, "请假记录不存在");
        }
        
        String newStatus = (String) data.get("status");
        
        if ("COUNSELOR".equals(role)) {
            SysUser student = userService.getById(leave.getStudentId());
            SysUser currentUser = userService.getById(userId);
            if (student == null || !student.getCollege().equals(currentUser.getCollege())) {
                return Result.error(403, "只能审批本学院学生的请假");
            }
            if (!"PENDING".equals(leave.getStatus())) {
                return Result.error(400, "只能审批待辅导员审批的请假");
            }
            if ("APPROVED".equals(newStatus)) {
                leave.setStatus("COUNSELOR_APPROVED");
            } else {
                leave.setStatus("REJECTED");
            }
        } else if ("ADMIN".equals(role)) {
            if (!"COUNSELOR_APPROVED".equals(leave.getStatus())) {
                return Result.error(400, "只能审批已通过辅导员审批的请假");
            }
            leave.setStatus(newStatus);
        } else {
            return Result.error(403, "无权审批");
        }
        
        leave.setApproverId(((Number) data.get("approverId")).longValue());
        leave.setApproveComment((String) data.get("comment"));
        leave.setApproveTime(LocalDateTime.now());
        dormLeaveService.updateById(leave);
        return Result.success("审批完成", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dormLeaveService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> getPending() {
        String role = jwtUtils.getCurrentUserRole(request);
        
        LambdaQueryWrapper<DormLeave> wrapper = new LambdaQueryWrapper<>();
        applyRoleFilter(wrapper);
        
        if ("COUNSELOR".equals(role)) {
            wrapper.eq(DormLeave::getStatus, "PENDING");
        } else if ("ADMIN".equals(role)) {
            wrapper.eq(DormLeave::getStatus, "COUNSELOR_APPROVED");
        } else {
            wrapper.eq(DormLeave::getStatus, "PENDING");
        }
        
        wrapper.orderByDesc(DormLeave::getCreateTime);
        List<DormLeave> leaves = dormLeaveService.list(wrapper);
        
        List<Map<String, Object>> result = leaves.stream().map(leave -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", leave.getId());
            map.put("studentId", leave.getStudentId());
            map.put("leaveType", leave.getLeaveType());
            map.put("reason", leave.getReason());
            map.put("startTime", leave.getStartTime());
            map.put("endTime", leave.getEndTime());
            map.put("status", leave.getStatus());
            
            SysUser student = userService.getById(leave.getStudentId());
            map.put("studentName", student != null ? student.getRealName() : "未知");
            
            return map;
        }).toList();
        
        return Result.success(result);
    }

    private String storeProofImage(String dataUrl) {
        int separator = dataUrl.indexOf(',');
        if (separator <= 0) {
            throw new IllegalArgumentException("凭证照片格式不正确");
        }

        String metadata = dataUrl.substring(0, separator).toLowerCase();
        String extension;
        if (metadata.startsWith("data:image/png;")) extension = ".png";
        else if (metadata.startsWith("data:image/jpeg;") || metadata.startsWith("data:image/jpg;")) extension = ".jpg";
        else if (metadata.startsWith("data:image/gif;")) extension = ".gif";
        else if (metadata.startsWith("data:image/webp;")) extension = ".webp";
        else throw new IllegalArgumentException("凭证照片仅支持 PNG、JPG、GIF 或 WEBP 格式");

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("凭证照片内容无法解析");
        }
        if (bytes.length == 0 || bytes.length > MAX_PROOF_IMAGE_BYTES) {
            throw new IllegalArgumentException("凭证照片大小必须在 5MB 以内");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path imagePath = PROOF_IMAGE_DIRECTORY.resolve(filename).normalize();
        try {
            Files.createDirectories(PROOF_IMAGE_DIRECTORY);
            Files.write(imagePath, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("保存凭证照片失败", e);
        }
        return "/api/leaves/proof/" + filename;
    }

    private void deleteStoredImage(String storedImage) {
        if (storedImage == null || !storedImage.startsWith("/api/leaves/proof/")) return;
        String filename = storedImage.substring(storedImage.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(PROOF_IMAGE_DIRECTORY.resolve(filename).normalize());
        } catch (IOException ignored) {
            // The database error is the primary failure; orphan cleanup can be retried later.
        }
    }
}
