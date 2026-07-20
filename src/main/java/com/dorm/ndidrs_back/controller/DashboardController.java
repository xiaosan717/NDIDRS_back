package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.DormCheckRecord;
import com.dorm.ndidrs_back.entity.DormHazard;
import com.dorm.ndidrs_back.entity.DormLeave;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.DormCheckRecordService;
import com.dorm.ndidrs_back.service.DormHazardService;
import com.dorm.ndidrs_back.service.DormLeaveService;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DormCheckRecordService checkRecordService;
    private final DormLeaveService leaveService;
    private final DormHazardService hazardService;
    private final SysUserService userService;
    private final DormRoomService roomService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequest request;

    public DashboardController(DormCheckRecordService checkRecordService,
                               DormLeaveService leaveService,
                               DormHazardService hazardService,
                               SysUserService userService,
                               DormRoomService roomService,
                               JwtUtils jwtUtils,
                               HttpServletRequest request) {
        this.checkRecordService = checkRecordService;
        this.leaveService = leaveService;
        this.hazardService = hazardService;
        this.userService = userService;
        this.roomService = roomService;
        this.jwtUtils = jwtUtils;
        this.request = request;
    }

    private static class ScopeFilter {
        List<Long> studentIds;
        List<Long> roomIds;
        boolean isAdmin;
        boolean isDormManager;
        String building;
    }

    private ScopeFilter getScopeFilter() {
        ScopeFilter filter = new ScopeFilter();
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null || "ADMIN".equals(role)) {
            filter.isAdmin = true;
            return filter;
        }

        SysUser currentUser = userService.getById(userId);
        if (currentUser == null) {
            filter.isAdmin = true;
            return filter;
        }

        if ("STUDENT".equals(role) || "DORM_LEADER".equals(role)) {
            filter.studentIds = List.of(userId);
        } else if ("COUNSELOR".equals(role)) {
            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                studentWrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            studentWrapper.eq(SysUser::getClassName, currentUser.getClassName())
                    .in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
            filter.studentIds = userService.list(studentWrapper).stream().map(SysUser::getId).toList();
        } else if ("DORM_MANAGER".equals(role)) {
            filter.isDormManager = true;
            filter.building = currentUser.getBuilding();
            filter.studentIds = userService.list(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getBuilding, currentUser.getBuilding())
                    .in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER")))
                    .stream().map(u -> u.getId()).toList();
            filter.roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getBuilding, currentUser.getBuilding()))
                    .stream().map(r -> r.getId()).toList();
        }
        return filter;
    }

    private LambdaQueryWrapper<DormCheckRecord> applyCheckRecordFilter(ScopeFilter filter) {
        LambdaQueryWrapper<DormCheckRecord> wrapper = new LambdaQueryWrapper<>();
        if (!filter.isAdmin) {
            if (filter.studentIds != null && !filter.studentIds.isEmpty()) {
                wrapper.in(DormCheckRecord::getStudentId, filter.studentIds);
            } else if (filter.roomIds != null && !filter.roomIds.isEmpty()) {
                wrapper.in(DormCheckRecord::getRoomId, filter.roomIds);
            } else {
                wrapper.eq(DormCheckRecord::getId, -1L);
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<DormLeave> applyLeaveFilter(ScopeFilter filter) {
        LambdaQueryWrapper<DormLeave> wrapper = new LambdaQueryWrapper<>();
        if (!filter.isAdmin) {
            if (filter.studentIds != null && !filter.studentIds.isEmpty()) {
                wrapper.in(DormLeave::getStudentId, filter.studentIds);
            } else {
                wrapper.eq(DormLeave::getId, -1L);
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<DormHazard> applyHazardFilter(ScopeFilter filter) {
        LambdaQueryWrapper<DormHazard> wrapper = new LambdaQueryWrapper<>();
        if (!filter.isAdmin) {
            if (filter.studentIds != null && !filter.studentIds.isEmpty()) {
                wrapper.in(DormHazard::getStudentId, filter.studentIds);
            } else if (filter.roomIds != null && !filter.roomIds.isEmpty()) {
                wrapper.in(DormHazard::getRoomId, filter.roomIds);
            } else {
                wrapper.eq(DormHazard::getId, -1L);
            }
        }
        return wrapper;
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        ScopeFilter filter = getScopeFilter();

        // 学生人数
        long studentCount;
        if (filter.isAdmin) {
            studentCount = userService.count(new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER")));
        } else if (filter.studentIds != null) {
            studentCount = filter.studentIds.size();
        } else {
            studentCount = 0;
        }
        stats.put("studentCount", studentCount);

        // 宿舍数量
        long roomCount;
        if (filter.isAdmin) {
            roomCount = roomService.count();
        } else if (filter.roomIds != null) {
            roomCount = filter.roomIds.size();
        } else if (filter.studentIds != null && !filter.studentIds.isEmpty()) {
            List<SysUser> students = userService.list(new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getId, filter.studentIds));
            roomCount = students.stream()
                    .filter(s -> s.getBuilding() != null && s.getRoom() != null)
                    .map(s -> s.getBuilding() + "-" + s.getRoom())
                    .distinct()
                    .count();
        } else {
            roomCount = 0;
        }
        stats.put("roomCount", roomCount);

        // 今日查寝次数
        LambdaQueryWrapper<DormCheckRecord> checkWrapper = applyCheckRecordFilter(filter);
        checkWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now());
        long todayCheckCount = checkRecordService.count(checkWrapper);
        stats.put("checkCount", todayCheckCount);

        // 待处理隐患数量
        LambdaQueryWrapper<DormHazard> hazardWrapper = applyHazardFilter(filter);
        hazardWrapper.eq(DormHazard::getStatus, "REPORTED");
        long hazardCount = hazardService.count(hazardWrapper);
        stats.put("hazardCount", hazardCount);

        return Result.success(stats);
    }

    @GetMapping("/pending")
    public Result<Map<String, Object>> getPendingTasks() {
        Map<String, Object> pending = new HashMap<>();
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        ScopeFilter filter = getScopeFilter();

        if ("ADMIN".equals(role)) {
            // 管理员:待审批请假(辅导员已批准)、待处理隐患(宿管已批准)
            LambdaQueryWrapper<DormLeave> leaveWrapper = new LambdaQueryWrapper<>();
            leaveWrapper.eq(DormLeave::getStatus, "COUNSELOR_APPROVED");
            long leaveCount = leaveService.count(leaveWrapper);
            pending.put("pendingLeaveApproval", leaveCount);

            LambdaQueryWrapper<DormHazard> hazardWrapper = new LambdaQueryWrapper<>();
            hazardWrapper.eq(DormHazard::getStatus, "MANAGER_APPROVED");
            long hazardCount = hazardService.count(hazardWrapper);
            pending.put("pendingHazardHandle", hazardCount);

            // 今日异常统计
            LambdaQueryWrapper<DormCheckRecord> lateWrapper = new LambdaQueryWrapper<>();
            lateWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now()).eq(DormCheckRecord::getStatus, "LATE");
            pending.put("todayLateCount", checkRecordService.count(lateWrapper));

            LambdaQueryWrapper<DormCheckRecord> absentWrapper = new LambdaQueryWrapper<>();
            absentWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now()).eq(DormCheckRecord::getStatus, "ABSENT");
            pending.put("todayAbsentCount", checkRecordService.count(absentWrapper));

        } else if ("COUNSELOR".equals(role)) {
            // 辅导员:待审批请假(PENDING)、本班异常
            LambdaQueryWrapper<DormLeave> leaveWrapper = applyLeaveFilter(filter);
            leaveWrapper.eq(DormLeave::getStatus, "PENDING");
            long leaveCount = leaveService.count(leaveWrapper);
            pending.put("pendingLeaveApproval", leaveCount);

            LambdaQueryWrapper<DormCheckRecord> lateWrapper = applyCheckRecordFilter(filter);
            lateWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now()).eq(DormCheckRecord::getStatus, "LATE");
            pending.put("todayLateCount", checkRecordService.count(lateWrapper));

            LambdaQueryWrapper<DormCheckRecord> absentWrapper = applyCheckRecordFilter(filter);
            absentWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now()).eq(DormCheckRecord::getStatus, "ABSENT");
            pending.put("todayAbsentCount", checkRecordService.count(absentWrapper));

        } else if ("DORM_MANAGER".equals(role)) {
            // 宿管:待处理隐患(REPORTED)、本楼栋未填报宿舍、异常
            LambdaQueryWrapper<DormHazard> hazardWrapper = applyHazardFilter(filter);
            hazardWrapper.eq(DormHazard::getStatus, "REPORTED");
            long hazardCount = hazardService.count(hazardWrapper);
            pending.put("pendingHazardApproval", hazardCount);

            // 本楼栋今日已填报宿舍数
            LambdaQueryWrapper<DormCheckRecord> checkedWrapper = applyCheckRecordFilter(filter);
            checkedWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now());
            long checkedRoomCount = checkRecordService.list(checkedWrapper).stream()
                    .map(DormCheckRecord::getRoomId).distinct().count();

            // 本楼栋总宿舍数
            long totalRoomCount = filter.roomIds != null ? filter.roomIds.size() : 0;
            pending.put("uncheckedRoomCount", totalRoomCount - checkedRoomCount);

            LambdaQueryWrapper<DormCheckRecord> lateWrapper = applyCheckRecordFilter(filter);
            lateWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now()).eq(DormCheckRecord::getStatus, "LATE");
            pending.put("todayLateCount", checkRecordService.count(lateWrapper));

            LambdaQueryWrapper<DormCheckRecord> absentWrapper = applyCheckRecordFilter(filter);
            absentWrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now()).eq(DormCheckRecord::getStatus, "ABSENT");
            pending.put("todayAbsentCount", checkRecordService.count(absentWrapper));

        } else if ("DORM_LEADER".equals(role)) {
            // 宿舍长:今日是否已填报
            LambdaQueryWrapper<DormCheckRecord> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(DormCheckRecord::getSubmitterId, userId)
                    .eq(DormCheckRecord::getCheckDate, LocalDate.now());
            pending.put("needTodayCheck", checkRecordService.count(checkWrapper) == 0 ? 1 : 0);

            // 本宿舍请假学生
            SysUser currentUser = userService.getById(userId);
            if (currentUser != null && currentUser.getBuilding() != null && currentUser.getRoom() != null) {
                DormRoom room = roomService.getOne(new LambdaQueryWrapper<DormRoom>()
                        .eq(DormRoom::getBuilding, currentUser.getBuilding())
                        .eq(DormRoom::getRoomNumber, currentUser.getRoom()));
                if (room != null) {
                    LambdaQueryWrapper<DormLeave> leaveWrapper = new LambdaQueryWrapper<>();
                    leaveWrapper.eq(DormLeave::getStudentId, userId).eq(DormLeave::getStatus, "APPROVED");
                    pending.put("myApprovedLeave", leaveService.count(leaveWrapper));
                }
            }

        } else if ("STUDENT".equals(role)) {
            // 学生:我的待审批请假、我的隐患状态
            LambdaQueryWrapper<DormLeave> leaveWrapper = new LambdaQueryWrapper<>();
            leaveWrapper.eq(DormLeave::getStudentId, userId)
                    .in(DormLeave::getStatus, java.util.Arrays.asList("PENDING", "COUNSELOR_APPROVED"));
            pending.put("myPendingLeave", leaveService.count(leaveWrapper));

            LambdaQueryWrapper<DormHazard> hazardWrapper = new LambdaQueryWrapper<>();
            hazardWrapper.eq(DormHazard::getStudentId, userId)
                    .in(DormHazard::getStatus, java.util.Arrays.asList("REPORTED", "MANAGER_APPROVED", "PROCESSING"));
            pending.put("myPendingHazard", hazardService.count(hazardWrapper));
        }

        return Result.success(pending);
    }

    @GetMapping("/recentRecords")
    public Result<List<Map<String, Object>>> getRecentRecords() {
        ScopeFilter filter = getScopeFilter();
        LambdaQueryWrapper<DormCheckRecord> wrapper = applyCheckRecordFilter(filter);
        wrapper.orderByDesc(DormCheckRecord::getCheckDate)
                .orderByDesc(DormCheckRecord::getCreateTime)
                .last("LIMIT 5");
        List<DormCheckRecord> records = checkRecordService.list(wrapper);
        
        List<Map<String, Object>> result = records.stream().map(record -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", record.getId());
            DormRoom room = roomService.getById(record.getRoomId());
            map.put("roomNumber", room != null ? room.getRoomNumber() : "未知");
            SysUser student = userService.getById(record.getStudentId());
            map.put("studentName", student != null ? student.getRealName() : "未知");
            map.put("status", record.getStatus());
            map.put("remark", record.getRemark());
            map.put("checkDate", record.getCheckDate());
            
            return map;
        }).toList();
        
        return Result.success(result);
    }

    @GetMapping("/statusDistribution")
    public Result<Map<String, Object>> getStatusDistribution() {
        Map<String, Object> distribution = new HashMap<>();
        ScopeFilter filter = getScopeFilter();

        // 取每个学生今日的查寝记录
        LambdaQueryWrapper<DormCheckRecord> wrapper = applyCheckRecordFilter(filter);
        wrapper.eq(DormCheckRecord::getCheckDate, LocalDate.now());
        List<DormCheckRecord> todayRecords = checkRecordService.list(wrapper);

        Map<Long, DormCheckRecord> todayByStudent = new HashMap<>();
        for (DormCheckRecord record : todayRecords) {
            todayByStudent.put(record.getStudentId(), record);
        }

        long inRoom = 0, leave = 0, late = 0, absent = 0;
        for (DormCheckRecord record : todayByStudent.values()) {
            switch (record.getStatus()) {
                case "IN_ROOM": inRoom++; break;
                case "LEAVE": leave++; break;
                case "LATE": late++; break;
                case "ABSENT": absent++; break;
            }
        }

        // 未填报人数 = 总学生数 - 今日有记录的学生数
        long totalStudents;
        if (filter.isAdmin) {
            totalStudents = userService.count(new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER")));
        } else if (filter.studentIds != null) {
            totalStudents = filter.studentIds.size();
        } else {
            totalStudents = 0;
        }
        long notReported = totalStudents - todayByStudent.size();
        if (notReported < 0) notReported = 0;

        distribution.put("inRoom", inRoom);
        distribution.put("leave", leave);
        distribution.put("late", late);
        distribution.put("absent", absent);
        distribution.put("notReported", notReported);
        distribution.put("total", totalStudents);

        return Result.success(distribution);
    }

    @GetMapping("/weeklyTrend")
    public Result<List<Map<String, Object>>> getWeeklyTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        ScopeFilter filter = getScopeFilter();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());

            LambdaQueryWrapper<DormCheckRecord> inRoomWrapper = applyCheckRecordFilter(filter);
            inRoomWrapper.eq(DormCheckRecord::getCheckDate, date).eq(DormCheckRecord::getStatus, "IN_ROOM");
            long inRoom = checkRecordService.count(inRoomWrapper);

            LambdaQueryWrapper<DormCheckRecord> leaveWrapper = applyCheckRecordFilter(filter);
            leaveWrapper.eq(DormCheckRecord::getCheckDate, date).eq(DormCheckRecord::getStatus, "LEAVE");
            long leave = checkRecordService.count(leaveWrapper);

            LambdaQueryWrapper<DormCheckRecord> lateWrapper = applyCheckRecordFilter(filter);
            lateWrapper.eq(DormCheckRecord::getCheckDate, date).eq(DormCheckRecord::getStatus, "LATE");
            long late = checkRecordService.count(lateWrapper);

            LambdaQueryWrapper<DormCheckRecord> absentWrapper = applyCheckRecordFilter(filter);
            absentWrapper.eq(DormCheckRecord::getCheckDate, date).eq(DormCheckRecord::getStatus, "ABSENT");
            long absent = checkRecordService.count(absentWrapper);

            dayData.put("inRoom", inRoom);
            dayData.put("leave", leave);
            dayData.put("late", late);
            dayData.put("absent", absent);
            dayData.put("total", inRoom + leave + late + absent);

            trend.add(dayData);
        }

        return Result.success(trend);
    }
}
