package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormCheckRecord;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.DormCheckRecordService;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkRecords")
public class DormCheckRecordController {
    private final DormCheckRecordService dormCheckRecordService;
    private final SysUserService userService;
    private final DormRoomService roomService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequest request;

    public DormCheckRecordController(DormCheckRecordService dormCheckRecordService,
                                      SysUserService userService,
                                      DormRoomService roomService,
                                      JwtUtils jwtUtils,
                                      HttpServletRequest request) {
        this.dormCheckRecordService = dormCheckRecordService;
        this.userService = userService;
        this.roomService = roomService;
        this.jwtUtils = jwtUtils;
        this.request = request;
    }

    private void applyRoleFilter(LambdaQueryWrapper<DormCheckRecord> wrapper) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null || "ADMIN".equals(role)) return;

        SysUser currentUser = userService.getById(userId);
        if (currentUser == null) return;

        if ("STUDENT".equals(role)) {
            wrapper.eq(DormCheckRecord::getStudentId, userId);
        } else if ("DORM_LEADER".equals(role)) {
            List<Long> roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getLeaderId, userId))
                    .stream().map(DormRoom::getId).toList();
            if (!roomIds.isEmpty()) {
                wrapper.in(DormCheckRecord::getRoomId, roomIds);
            }
        } else if ("COUNSELOR".equals(role)) {
            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                studentWrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            studentWrapper.eq(SysUser::getClassName, currentUser.getClassName())
                    .in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
            List<Long> studentIds = userService.list(studentWrapper)
                    .stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormCheckRecord::getStudentId, studentIds);
            } else {
                wrapper.eq(DormCheckRecord::getId, -1L);
            }
        } else if ("DORM_MANAGER".equals(role)) {
            List<Long> roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getBuilding, currentUser.getBuilding()))
                    .stream().map(DormRoom::getId).toList();
            if (!roomIds.isEmpty()) {
                wrapper.in(DormCheckRecord::getRoomId, roomIds);
            } else {
                wrapper.eq(DormCheckRecord::getId, -1L);
            }
        }
    }

    @GetMapping
    public Result<Page<Map<String, Object>>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) Long roomId,
                                                    @RequestParam(required = false) Long studentId,
                                                    @RequestParam(required = false) LocalDate checkDate,
                                                    @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DormCheckRecord> wrapper = new LambdaQueryWrapper<>();
        applyRoleFilter(wrapper);
        if (roomId != null) wrapper.eq(DormCheckRecord::getRoomId, roomId);
        if (studentId != null) wrapper.eq(DormCheckRecord::getStudentId, studentId);
        if (checkDate != null) wrapper.eq(DormCheckRecord::getCheckDate, checkDate);
        if (status != null) wrapper.eq(DormCheckRecord::getStatus, status);
        wrapper.orderByDesc(DormCheckRecord::getCheckDate);
        Page<DormCheckRecord> page = dormCheckRecordService.page(new Page<>(pageNum, pageSize), wrapper);
        
        List<Map<String, Object>> records = page.getRecords().stream().map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.getId());
            map.put("roomId", record.getRoomId());
            map.put("studentId", record.getStudentId());
            map.put("checkDate", record.getCheckDate());
            map.put("status", record.getStatus());
            map.put("remark", record.getRemark());
            map.put("submitTime", record.getSubmitTime());
            map.put("isModified", record.getIsModified());
            map.put("modifyRemark", record.getModifyRemark());
            
            SysUser student = userService.getById(record.getStudentId());
            map.put("studentName", student != null ? student.getRealName() : "未知");
            
            DormRoom room = roomService.getById(record.getRoomId());
            map.put("roomNumber", room != null ? room.getRoomNumber() : "未知");
            
            return map;
        }).toList();
        
        Page<Map<String, Object>> resultPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        resultPage.setRecords(records);
        
        return Result.success(resultPage);
    }

    @GetMapping("/{id}")
    public Result<DormCheckRecord> getById(@PathVariable Long id) {
        return Result.success(dormCheckRecordService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody DormCheckRecord record) {
        record.setSubmitTime(LocalDateTime.now());
        dormCheckRecordService.save(record);
        return Result.success("填报成功", null);
    }

    @PostMapping("/batch")
    public Result<Void> batchAdd(@RequestBody Map<String, Object> data) {
        List<Map<String, Object>> recordList = (List<Map<String, Object>>) data.get("records");
        LocalDate today = LocalDate.now();
        
        List<DormCheckRecord> records = recordList.stream().map(item -> {
            DormCheckRecord r = new DormCheckRecord();
            r.setRoomId(((Number) item.get("roomId")).longValue());
            r.setStudentId(((Number) item.get("studentId")).longValue());
            r.setStatus((String) item.get("status"));
            r.setRemark((String) item.get("remark"));
            r.setSubmitTime(LocalDateTime.now());
            r.setCheckDate(today);
            return r;
        }).toList();
        
        for (DormCheckRecord r : records) {
            DormCheckRecord existing = dormCheckRecordService.getOne(
                new LambdaQueryWrapper<DormCheckRecord>()
                    .eq(DormCheckRecord::getStudentId, r.getStudentId())
                    .eq(DormCheckRecord::getCheckDate, today));
            if (existing != null) {
                r.setId(existing.getId());
            }
        }
        dormCheckRecordService.saveOrUpdateBatch(records);
        return Result.success("批量填报成功", null);
    }

    @PutMapping("/modify/{id}")
    public Result<Void> modify(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        DormCheckRecord record = dormCheckRecordService.getById(id);
        record.setStatus((String) data.get("status"));
        record.setModifyRemark((String) data.get("modifyRemark"));
        record.setModifierId(((Number) data.get("modifierId")).longValue());
        record.setModifyTime(LocalDateTime.now());
        record.setIsModified(1);
        dormCheckRecordService.updateById(record);
        return Result.success("修正成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dormCheckRecordService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/uncheckedRooms")
    public Result<List<Map<String, Object>>> getUncheckedRooms(@RequestParam(required = false) String building) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        
        if (!"DORM_MANAGER".equals(role)) {
            return Result.error(403, "只有宿管可以查看未填报宿舍");
        }
        
        SysUser currentUser = userService.getById(userId);
        String targetBuilding = building != null ? building : 
            (currentUser != null ? currentUser.getBuilding() : null);
        
        if (targetBuilding == null) {
            return Result.error(400, "楼栋信息为空");
        }

        List<DormRoom> allRooms = roomService.list(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getBuilding, targetBuilding));
        
        List<Long> checkedRoomIds = dormCheckRecordService.list(new LambdaQueryWrapper<DormCheckRecord>()
                .eq(DormCheckRecord::getCheckDate, LocalDate.now()))
                .stream().map(DormCheckRecord::getRoomId).distinct().toList();

        List<Map<String, Object>> uncheckedRooms = allRooms.stream()
                .filter(room -> !checkedRoomIds.contains(room.getId()))
                .filter(room -> {
                    long studentCount = userService.count(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getBuilding, room.getBuilding())
                            .eq(SysUser::getRoom, room.getRoomNumber())
                            .eq(SysUser::getStatus, 1));
                    return studentCount > 0;
                })
                .map(room -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", room.getId());
                    map.put("roomNumber", room.getRoomNumber());
                    map.put("building", room.getBuilding());
                    map.put("floor", room.getFloor());
                    return map;
                }).toList();

        return Result.success(uncheckedRooms);
    }

    @GetMapping("/today/{roomId}")
    public Result<List<DormCheckRecord>> getTodayRecords(@PathVariable Long roomId) {
        LambdaQueryWrapper<DormCheckRecord> wrapper = new LambdaQueryWrapper<>();
        applyRoleFilter(wrapper);
        wrapper.eq(DormCheckRecord::getRoomId, roomId)
                .eq(DormCheckRecord::getCheckDate, LocalDate.now());
        List<DormCheckRecord> records = dormCheckRecordService.list(wrapper);
        return Result.success(records);
    }

    @GetMapping("/byStudent/{studentId}")
    public Result<List<DormCheckRecord>> getByStudent(@PathVariable Long studentId) {
        LambdaQueryWrapper<DormCheckRecord> wrapper = new LambdaQueryWrapper<>();
        applyRoleFilter(wrapper);
        wrapper.eq(DormCheckRecord::getStudentId, studentId)
                .orderByDesc(DormCheckRecord::getCheckDate);
        List<DormCheckRecord> records = dormCheckRecordService.list(wrapper);
        return Result.success(records);
    }
}