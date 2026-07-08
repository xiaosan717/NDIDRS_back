package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormHazard;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.DormHazardService;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hazards")
public class DormHazardController {
    private final DormHazardService dormHazardService;
    private final SysUserService userService;
    private final DormRoomService roomService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequest request;

    public DormHazardController(DormHazardService dormHazardService, SysUserService userService,
                                DormRoomService roomService, JwtUtils jwtUtils, HttpServletRequest request) {
        this.dormHazardService = dormHazardService;
        this.userService = userService;
        this.roomService = roomService;
        this.jwtUtils = jwtUtils;
        this.request = request;
    }

    private void applyRoleFilter(LambdaQueryWrapper<DormHazard> wrapper) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null || "ADMIN".equals(role)) return;

        SysUser currentUser = userService.getById(userId);
        if (currentUser == null) return;

        if ("STUDENT".equals(role) || "DORM_LEADER".equals(role)) {
            wrapper.eq(DormHazard::getStudentId, userId);
        } else if ("COUNSELOR".equals(role)) {
            List<Long> studentIds = userService.list(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getCollege, currentUser.getCollege())
                    .eq(SysUser::getRole, "STUDENT"))
                    .stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormHazard::getStudentId, studentIds);
            } else {
                wrapper.eq(DormHazard::getId, -1L);
            }
        } else if ("DORM_MANAGER".equals(role)) {
            List<Long> roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getBuilding, currentUser.getBuilding()))
                    .stream().map(DormRoom::getId).toList();
            if (!roomIds.isEmpty()) {
                wrapper.in(DormHazard::getRoomId, roomIds);
            } else {
                wrapper.eq(DormHazard::getId, -1L);
            }
        }
    }

    @GetMapping
    public Result<Page<DormHazard>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) Long studentId,
                                          @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DormHazard> wrapper = new LambdaQueryWrapper<>();
        applyRoleFilter(wrapper);
        if (studentId != null) wrapper.eq(DormHazard::getStudentId, studentId);
        if (status != null) wrapper.eq(DormHazard::getStatus, status);
        wrapper.orderByDesc(DormHazard::getCreateTime);
        Page<DormHazard> page = dormHazardService.page(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<DormHazard> getById(@PathVariable Long id) {
        return Result.success(dormHazardService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody DormHazard hazard) {
        // 根据学生信息自动查找正确的 room_id
        SysUser student = userService.getById(hazard.getStudentId());
        if (student == null) {
            return Result.error(404, "学生不存在");
        }
        if (student.getBuilding() == null || student.getRoom() == null) {
            return Result.error(400, "学生未绑定宿舍信息");
        }
        
        // 查询 dorm_room 表获取正确的 room_id
        DormRoom room = roomService.getOne(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getBuilding, student.getBuilding())
                .eq(DormRoom::getRoomNumber, student.getRoom()));
        if (room == null) {
            return Result.error(404, "宿舍信息不存在");
        }
        
        hazard.setRoomId(room.getId());
        hazard.setStatus("REPORTED");
        dormHazardService.save(hazard);
        return Result.success("隐患上报成功", null);
    }

    @PutMapping("/handle/{id}")
    public Result<Void> handle(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        DormHazard hazard = dormHazardService.getById(id);
        if (hazard == null) {
            return Result.error(404, "隐患记录不存在");
        }

        String newStatus = (String) data.get("status");
        String handleRemark = (String) data.get("handleRemark");

        if ("DORM_MANAGER".equals(role)) {
            // 宿管只能审批 REPORTED 状态的隐患
            if (!"REPORTED".equals(hazard.getStatus())) {
                return Result.error(400, "只能审批上报状态的隐患,当前状态为: " + hazard.getStatus());
            }

            // 同楼栋验证
            SysUser currentUser = userService.getById(userId);
            DormRoom room = roomService.getById(hazard.getRoomId());
            if (currentUser == null || room == null) {
                return Result.error(403, "无权处理该隐患");
            }
            if (!room.getBuilding().equals(currentUser.getBuilding())) {
                return Result.error(403, "只能处理本楼栋的隐患,该隐患属于: " + room.getBuilding());
            }

            // 宿管审批:批准或拒绝
            if ("MANAGER_APPROVED".equals(newStatus)) {
                hazard.setStatus("MANAGER_APPROVED");
                hazard.setHandleRemark("宿管已批准,等待管理员处理");
            } else if ("REJECTED".equals(newStatus)) {
                hazard.setStatus("REJECTED");
                hazard.setHandleRemark(handleRemark != null ? handleRemark : "宿管已拒绝");
            } else {
                return Result.error(400, "宿管只能批准或拒绝隐患");
            }
            hazard.setHandlerId(userId);
            hazard.setHandleTime(LocalDateTime.now());

        } else if ("ADMIN".equals(role)) {
            // 管理员只能处理 MANAGER_APPROVED 状态的隐患
            if (!"MANAGER_APPROVED".equals(hazard.getStatus())) {
                return Result.error(400, "只能处理宿管已批准的隐患,当前状态为: " + hazard.getStatus() +
                    ",需等待宿管先审批");
            }

            // 管理员处理:开始处理或完成或拒绝
            if ("PROCESSING".equals(newStatus)) {
                hazard.setStatus("PROCESSING");
                hazard.setHandleRemark(handleRemark != null ? handleRemark : "管理员已受理,正在处理");
            } else if ("COMPLETED".equals(newStatus)) {
                hazard.setStatus("COMPLETED");
                hazard.setHandleRemark(handleRemark != null ? handleRemark : "隐患已处理完成");
            } else if ("REJECTED".equals(newStatus)) {
                hazard.setStatus("REJECTED");
                hazard.setHandleRemark(handleRemark != null ? handleRemark : "管理员已拒绝处理");
            } else {
                return Result.error(400, "管理员只能开始处理、完成或拒绝隐患");
            }
            hazard.setHandlerId(userId);
            hazard.setHandleTime(LocalDateTime.now());

        } else {
            return Result.error(403, "只有宿管和管理员有权处理隐患");
        }

        dormHazardService.updateById(hazard);
        return Result.success("处理成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dormHazardService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> getPending() {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        
        LambdaQueryWrapper<DormHazard> wrapper = new LambdaQueryWrapper<>();
        
        if ("DORM_MANAGER".equals(role)) {
            wrapper.eq(DormHazard::getStatus, "REPORTED");
            SysUser currentUser = userService.getById(userId);
            if (currentUser != null && currentUser.getBuilding() != null) {
                List<Long> roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                        .eq(DormRoom::getBuilding, currentUser.getBuilding()))
                        .stream().map(DormRoom::getId).toList();
                if (!roomIds.isEmpty()) {
                    wrapper.in(DormHazard::getRoomId, roomIds);
                } else {
                    wrapper.eq(DormHazard::getId, -1L);
                }
            }
        } else if ("ADMIN".equals(role)) {
            wrapper.eq(DormHazard::getStatus, "MANAGER_APPROVED");
        } else {
            wrapper.eq(DormHazard::getId, -1L);
        }
        
        wrapper.orderByDesc(DormHazard::getCreateTime);
        List<DormHazard> hazards = dormHazardService.list(wrapper);
        
        List<Map<String, Object>> result = hazards.stream().map(hazard -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", hazard.getId());
            map.put("studentId", hazard.getStudentId());
            map.put("roomId", hazard.getRoomId());
            map.put("hazardType", hazard.getHazardType());
            map.put("description", hazard.getDescription());
            map.put("status", hazard.getStatus());
            
            SysUser student = userService.getById(hazard.getStudentId());
            map.put("studentName", student != null ? student.getRealName() : "未知");
            
            DormRoom room = roomService.getById(hazard.getRoomId());
            map.put("roomNumber", room != null ? room.getRoomNumber() : "未知");
            map.put("building", room != null ? room.getBuilding() : "未知");
            
            return map;
        }).toList();
        
        return Result.success(result);
    }
}
