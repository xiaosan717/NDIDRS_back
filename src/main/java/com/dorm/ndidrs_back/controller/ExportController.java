package com.dorm.ndidrs_back.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.entity.DormCheckRecord;
import com.dorm.ndidrs_back.entity.DormHazard;
import com.dorm.ndidrs_back.entity.DormLeave;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.service.DormCheckRecordService;
import com.dorm.ndidrs_back.service.DormHazardService;
import com.dorm.ndidrs_back.service.DormLeaveService;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final DormCheckRecordService checkRecordService;
    private final DormLeaveService leaveService;
    private final DormHazardService hazardService;
    private final SysUserService userService;
    private final DormRoomService roomService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequest request;

    public ExportController(DormCheckRecordService checkRecordService,
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

    @GetMapping("/checkRecords")
    public void exportCheckRecords(HttpServletResponse response,
                                   @RequestParam(required = false) LocalDate startDate,
                                   @RequestParam(required = false) LocalDate endDate) throws IOException {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        SysUser currentUser = userService.getById(userId);

        LambdaQueryWrapper<DormCheckRecord> wrapper = new LambdaQueryWrapper<>();
        
        if (startDate != null) wrapper.ge(DormCheckRecord::getCheckDate, startDate);
        if (endDate != null) wrapper.le(DormCheckRecord::getCheckDate, endDate);
        
        applyRoleFilter(wrapper, role, currentUser);
        
        wrapper.orderByDesc(DormCheckRecord::getCheckDate);
        List<DormCheckRecord> records = checkRecordService.list(wrapper);

        List<CheckRecordExcelData> excelData = new ArrayList<>();
        for (DormCheckRecord record : records) {
            SysUser student = userService.getById(record.getStudentId());
            CheckRecordExcelData data = new CheckRecordExcelData();
            data.setCheckDate(record.getCheckDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            data.setStudentName(student != null ? student.getRealName() : "未知");
            data.setStudentId(student != null ? student.getUsername() : "");
            data.setClassName(student != null ? student.getClassName() : "");
            data.setBuilding(student != null ? student.getBuilding() : "");
            data.setRoom(student != null ? student.getRoom() : "");
            data.setStatus(convertStatus(record.getStatus()));
            data.setRemark(record.getRemark());
            data.setSubmitTime(record.getSubmitTime() != null ? 
                record.getSubmitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            excelData.add(data);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("查寝记录_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), CheckRecordExcelData.class).sheet("查寝记录").doWrite(excelData);
    }

    @GetMapping("/leaveRecords")
    public void exportLeaveRecords(HttpServletResponse response,
                                   @RequestParam(required = false) LocalDate startDate,
                                   @RequestParam(required = false) LocalDate endDate) throws IOException {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        SysUser currentUser = userService.getById(userId);

        LambdaQueryWrapper<DormLeave> wrapper = new LambdaQueryWrapper<>();
        
        if (startDate != null) wrapper.ge(DormLeave::getStartTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.le(DormLeave::getEndTime, endDate.atTime(23, 59, 59));
        
        applyLeaveRoleFilter(wrapper, role, currentUser);
        
        wrapper.orderByDesc(DormLeave::getCreateTime);
        List<DormLeave> leaves = leaveService.list(wrapper);

        List<LeaveExcelData> excelData = new ArrayList<>();
        for (DormLeave leave : leaves) {
            SysUser student = userService.getById(leave.getStudentId());
            LeaveExcelData data = new LeaveExcelData();
            data.setStudentName(student != null ? student.getRealName() : "未知");
            data.setStudentId(student != null ? student.getUsername() : "");
            data.setClassName(student != null ? student.getClassName() : "");
            data.setBuilding(student != null ? student.getBuilding() : "");
            data.setRoom(student != null ? student.getRoom() : "");
            data.setLeaveType(convertLeaveType(leave.getLeaveType()));
            data.setStartDate(leave.getStartTime() != null ? 
                leave.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "");
            data.setEndDate(leave.getEndTime() != null ? 
                leave.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "");
            data.setReason(leave.getReason());
            data.setStatus(convertLeaveStatus(leave.getStatus()));
            data.setCreateTime(leave.getCreateTime() != null ? 
                leave.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            excelData.add(data);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("请假记录_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), LeaveExcelData.class).sheet("请假记录").doWrite(excelData);
    }

    @GetMapping("/hazardRecords")
    public void exportHazardRecords(HttpServletResponse response,
                                    @RequestParam(required = false) LocalDate startDate,
                                    @RequestParam(required = false) LocalDate endDate) throws IOException {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        SysUser currentUser = userService.getById(userId);

        LambdaQueryWrapper<DormHazard> wrapper = new LambdaQueryWrapper<>();
        
        if (startDate != null) wrapper.ge(DormHazard::getCreateTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.le(DormHazard::getCreateTime, endDate.atTime(23, 59, 59));
        
        applyHazardRoleFilter(wrapper, role, currentUser);
        
        wrapper.orderByDesc(DormHazard::getCreateTime);
        List<DormHazard> hazards = hazardService.list(wrapper);

        List<HazardExcelData> excelData = new ArrayList<>();
        for (DormHazard hazard : hazards) {
            SysUser student = userService.getById(hazard.getStudentId());
            DormRoom room = hazard.getRoomId() != null ? roomService.getById(hazard.getRoomId()) : null;
            HazardExcelData data = new HazardExcelData();
            data.setStudentName(student != null ? student.getRealName() : "未知");
            data.setStudentId(student != null ? student.getUsername() : "");
            data.setBuilding(room != null ? room.getBuilding() : (student != null ? student.getBuilding() : ""));
            data.setRoom(room != null ? room.getRoomNumber() : (student != null ? student.getRoom() : ""));
            data.setHazardType(convertHazardType(hazard.getHazardType()));
            data.setDescription(hazard.getDescription());
            data.setStatus(convertHazardStatus(hazard.getStatus()));
            data.setReportTime(hazard.getCreateTime() != null ? 
                hazard.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            data.setHandleRemark(hazard.getHandleRemark());
            data.setHandleTime(hazard.getHandleTime() != null ? 
                hazard.getHandleTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            excelData.add(data);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("隐患记录_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), HazardExcelData.class).sheet("隐患记录").doWrite(excelData);
    }

    @GetMapping("/userRecords")
    public void exportUserRecords(HttpServletResponse response) throws IOException {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        SysUser currentUser = userService.getById(userId);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        
        if ("COUNSELOR".equals(role) && currentUser != null) {
            wrapper.eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                wrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            wrapper.eq(SysUser::getClassName, currentUser.getClassName());
            wrapper.in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
        } else if ("DORM_MANAGER".equals(role) && currentUser != null) {
            wrapper.eq(SysUser::getBuilding, currentUser.getBuilding());
            wrapper.in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
        } else if (!"ADMIN".equals(role)) {
            return;
        }

        wrapper.orderByDesc(SysUser::getCreateTime);
        List<SysUser> users = userService.list(wrapper);

        List<UserExcelData> excelData = new ArrayList<>();
        for (SysUser user : users) {
            UserExcelData data = new UserExcelData();
            data.setUsername(user.getUsername());
            data.setRealName(user.getRealName());
            data.setRole(convertRole(user.getRole()));
            data.setCollege(user.getCollege());
            data.setGrade(user.getGrade());
            data.setClassName(user.getClassName());
            data.setBuilding(user.getBuilding());
            data.setRoom(user.getRoom());
            data.setPhone(user.getPhone());
            data.setStatus(user.getStatus() == 1 ? "启用" : "禁用");
            data.setCreateTime(user.getCreateTime() != null ? 
                user.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            excelData.add(data);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户列表_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), UserExcelData.class).sheet("用户列表").doWrite(excelData);
    }

    private void applyRoleFilter(LambdaQueryWrapper<DormCheckRecord> wrapper, String role, SysUser currentUser) {
        if ("ADMIN".equals(role)) return;
        
        if (currentUser == null) {
            wrapper.eq(DormCheckRecord::getId, -1L);
            return;
        }

        if ("STUDENT".equals(role)) {
            wrapper.eq(DormCheckRecord::getStudentId, currentUser.getId());
        } else if ("DORM_LEADER".equals(role)) {
            wrapper.eq(DormCheckRecord::getSubmitterId, currentUser.getId());
        } else if ("COUNSELOR".equals(role)) {
            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                studentWrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            studentWrapper.eq(SysUser::getClassName, currentUser.getClassName());
            studentWrapper.in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
            List<Long> studentIds = userService.list(studentWrapper).stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormCheckRecord::getStudentId, studentIds);
            } else {
                wrapper.eq(DormCheckRecord::getId, -1L);
            }
        } else if ("DORM_MANAGER".equals(role)) {
            wrapper.eq(DormCheckRecord::getSubmitterId, currentUser.getId());
        }
    }

    private void applyLeaveRoleFilter(LambdaQueryWrapper<DormLeave> wrapper, String role, SysUser currentUser) {
        if ("ADMIN".equals(role)) return;
        
        if (currentUser == null) {
            wrapper.eq(DormLeave::getId, -1L);
            return;
        }

        if ("STUDENT".equals(role) || "DORM_LEADER".equals(role)) {
            wrapper.eq(DormLeave::getStudentId, currentUser.getId());
        } else if ("COUNSELOR".equals(role)) {
            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                studentWrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            studentWrapper.eq(SysUser::getClassName, currentUser.getClassName());
            studentWrapper.in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
            List<Long> studentIds = userService.list(studentWrapper).stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormLeave::getStudentId, studentIds);
            } else {
                wrapper.eq(DormLeave::getId, -1L);
            }
        } else if ("DORM_MANAGER".equals(role)) {
            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.eq(SysUser::getBuilding, currentUser.getBuilding());
            studentWrapper.in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
            List<Long> studentIds = userService.list(studentWrapper).stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormLeave::getStudentId, studentIds);
            } else {
                wrapper.eq(DormLeave::getId, -1L);
            }
        }
    }

    private void applyHazardRoleFilter(LambdaQueryWrapper<DormHazard> wrapper, String role, SysUser currentUser) {
        if ("ADMIN".equals(role)) return;
        
        if (currentUser == null) {
            wrapper.eq(DormHazard::getId, -1L);
            return;
        }

        if ("STUDENT".equals(role) || "DORM_LEADER".equals(role)) {
            wrapper.eq(DormHazard::getStudentId, currentUser.getId());
        } else if ("COUNSELOR".equals(role)) {
            LambdaQueryWrapper<SysUser> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.eq(SysUser::getCollege, currentUser.getCollege());
            if (currentUser.getGrade() != null) {
                studentWrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            studentWrapper.eq(SysUser::getClassName, currentUser.getClassName());
            studentWrapper.in(SysUser::getRole, java.util.Arrays.asList("STUDENT", "DORM_LEADER"));
            List<Long> studentIds = userService.list(studentWrapper).stream().map(SysUser::getId).toList();
            if (!studentIds.isEmpty()) {
                wrapper.in(DormHazard::getStudentId, studentIds);
            } else {
                wrapper.eq(DormHazard::getId, -1L);
            }
        } else if ("DORM_MANAGER".equals(role)) {
            if (currentUser.getBuilding() != null) {
                List<Long> roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                        .eq(DormRoom::getBuilding, currentUser.getBuilding()))
                        .stream().map(DormRoom::getId).toList();
                if (!roomIds.isEmpty()) {
                    wrapper.in(DormHazard::getRoomId, roomIds);
                } else {
                    wrapper.eq(DormHazard::getId, -1L);
                }
            } else {
                wrapper.eq(DormHazard::getId, -1L);
            }
        }
    }

    private String convertStatus(String status) {
        return switch (status) {
            case "IN_ROOM" -> "在寝";
            case "LEAVE" -> "请假";
            case "LATE" -> "晚归";
            case "ABSENT" -> "缺勤";
            default -> status;
        };
    }

    private String convertLeaveType(String type) {
        return switch (type) {
            case "SICK" -> "病假";
            case "PERSONAL" -> "事假";
            case "OTHER" -> "其他";
            default -> type;
        };
    }

    private String convertLeaveStatus(String status) {
        return switch (status) {
            case "PENDING" -> "待审批";
            case "COUNSELOR_APPROVED" -> "辅导员已批准";
            case "APPROVED" -> "已批准";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }

    private String convertHazardType(String type) {
        return switch (type) {
            case "FIRE" -> "消防隐患";
            case "ELECTRICAL" -> "用电隐患";
            case "HYGIENE" -> "卫生问题";
            case "FACILITY" -> "设施损坏";
            case "OTHER" -> "其他";
            default -> type;
        };
    }

    private String convertHazardStatus(String status) {
        return switch (status) {
            case "REPORTED" -> "已上报";
            case "MANAGER_APPROVED" -> "宿管已批准";
            case "PROCESSING" -> "处理中";
            case "COMPLETED" -> "已完成";
            case "REJECTED" -> "已拒绝";
            default -> status;
        };
    }

    private String convertRole(String role) {
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "COUNSELOR" -> "辅导员";
            case "DORM_MANAGER" -> "宿管";
            case "DORM_LEADER" -> "宿舍长";
            case "STUDENT" -> "学生";
            default -> role;
        };
    }

    public static class CheckRecordExcelData {
        private String checkDate;
        @com.alibaba.excel.annotation.ExcelProperty("学生姓名")
        private String studentName;
        @com.alibaba.excel.annotation.ExcelProperty("学号")
        private String studentId;
        @com.alibaba.excel.annotation.ExcelProperty("班级")
        private String className;
        @com.alibaba.excel.annotation.ExcelProperty("楼栋")
        private String building;
        @com.alibaba.excel.annotation.ExcelProperty("宿舍")
        private String room;
        @com.alibaba.excel.annotation.ExcelProperty("状态")
        private String status;
        @com.alibaba.excel.annotation.ExcelProperty("备注")
        private String remark;
        @com.alibaba.excel.annotation.ExcelProperty("提交时间")
        private String submitTime;

        public String getCheckDate() { return checkDate; }
        public void setCheckDate(String checkDate) { this.checkDate = checkDate; }
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getBuilding() { return building; }
        public void setBuilding(String building) { this.building = building; }
        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getSubmitTime() { return submitTime; }
        public void setSubmitTime(String submitTime) { this.submitTime = submitTime; }
    }

    public static class LeaveExcelData {
        private String studentName;
        @com.alibaba.excel.annotation.ExcelProperty("学号")
        private String studentId;
        @com.alibaba.excel.annotation.ExcelProperty("班级")
        private String className;
        @com.alibaba.excel.annotation.ExcelProperty("楼栋")
        private String building;
        @com.alibaba.excel.annotation.ExcelProperty("宿舍")
        private String room;
        @com.alibaba.excel.annotation.ExcelProperty("请假类型")
        private String leaveType;
        @com.alibaba.excel.annotation.ExcelProperty("开始日期")
        private String startDate;
        @com.alibaba.excel.annotation.ExcelProperty("结束日期")
        private String endDate;
        @com.alibaba.excel.annotation.ExcelProperty("请假原因")
        private String reason;
        @com.alibaba.excel.annotation.ExcelProperty("状态")
        private String status;
        @com.alibaba.excel.annotation.ExcelProperty("申请时间")
        private String createTime;

        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getBuilding() { return building; }
        public void setBuilding(String building) { this.building = building; }
        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }
        public String getLeaveType() { return leaveType; }
        public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }

    public static class HazardExcelData {
        private String studentName;
        @com.alibaba.excel.annotation.ExcelProperty("学号")
        private String studentId;
        @com.alibaba.excel.annotation.ExcelProperty("楼栋")
        private String building;
        @com.alibaba.excel.annotation.ExcelProperty("宿舍")
        private String room;
        @com.alibaba.excel.annotation.ExcelProperty("隐患类型")
        private String hazardType;
        @com.alibaba.excel.annotation.ExcelProperty("隐患描述")
        private String description;
        @com.alibaba.excel.annotation.ExcelProperty("状态")
        private String status;
        @com.alibaba.excel.annotation.ExcelProperty("上报时间")
        private String reportTime;
        @com.alibaba.excel.annotation.ExcelProperty("处理结果")
        private String handleRemark;
        @com.alibaba.excel.annotation.ExcelProperty("处理时间")
        private String handleTime;

        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getBuilding() { return building; }
        public void setBuilding(String building) { this.building = building; }
        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }
        public String getHazardType() { return hazardType; }
        public void setHazardType(String hazardType) { this.hazardType = hazardType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReportTime() { return reportTime; }
        public void setReportTime(String reportTime) { this.reportTime = reportTime; }
        public String getHandleRemark() { return handleRemark; }
        public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
        public String getHandleTime() { return handleTime; }
        public void setHandleTime(String handleTime) { this.handleTime = handleTime; }
    }

    public static class UserExcelData {
        private String username;
        @com.alibaba.excel.annotation.ExcelProperty("真实姓名")
        private String realName;
        @com.alibaba.excel.annotation.ExcelProperty("角色")
        private String role;
        @com.alibaba.excel.annotation.ExcelProperty("学院")
        private String college;
        @com.alibaba.excel.annotation.ExcelProperty("年级")
        private String grade;
        @com.alibaba.excel.annotation.ExcelProperty("班级")
        private String className;
        @com.alibaba.excel.annotation.ExcelProperty("楼栋")
        private String building;
        @com.alibaba.excel.annotation.ExcelProperty("宿舍")
        private String room;
        @com.alibaba.excel.annotation.ExcelProperty("手机号")
        private String phone;
        @com.alibaba.excel.annotation.ExcelProperty("状态")
        private String status;
        @com.alibaba.excel.annotation.ExcelProperty("创建时间")
        private String createTime;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getCollege() { return college; }
        public void setCollege(String college) { this.college = college; }
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getBuilding() { return building; }
        public void setBuilding(String building) { this.building = building; }
        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }
}
