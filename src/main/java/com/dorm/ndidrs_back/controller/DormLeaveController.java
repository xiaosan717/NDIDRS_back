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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
public class DormLeaveController {
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
        leave.setStatus("PENDING");
        dormLeaveService.save(leave);
        return Result.success("申请提交成功", null);
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
}
