package com.dorm.ndidrs_back.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dorm.ndidrs_back.dto.AiAnalysisRequest;
import com.dorm.ndidrs_back.dto.AiAnalysisResponse;
import com.dorm.ndidrs_back.entity.DormCheckRecord;
import com.dorm.ndidrs_back.entity.DormHazard;
import com.dorm.ndidrs_back.entity.DormLeave;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);
    private static final List<String> STAFF_ROLES = List.of("ADMIN", "COUNSELOR", "DORM_MANAGER");
    private static final List<String> OPEN_HAZARD_STATUSES = List.of("REPORTED", "MANAGER_APPROVED", "PROCESSING");

    private final DormCheckRecordService checkRecordService;
    private final DormHazardService hazardService;
    private final DormLeaveService leaveService;
    private final DormRoomService roomService;
    private final SysUserService userService;
    private final ObjectMapper objectMapper;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${ai.base-url:https://api.deepseek.com}")
    private String aiBaseUrl;

    @Value("${ai.api-key:}")
    private String aiApiKey;

    @Value("${ai.model:deepseek-v4-flash}")
    private String aiModel;

    @Value("${ai.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${ai.read-timeout-seconds:60}")
    private int readTimeoutSeconds;

    public AiAnalysisService(DormCheckRecordService checkRecordService,
                             DormHazardService hazardService,
                             DormLeaveService leaveService,
                             DormRoomService roomService,
                             SysUserService userService,
                             ObjectMapper objectMapper) {
        this.checkRecordService = checkRecordService;
        this.hazardService = hazardService;
        this.leaveService = leaveService;
        this.roomService = roomService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public AiAnalysisResponse analyze(Long userId, String role, AiAnalysisRequest request) {
        if (userId == null || role == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        if (!STAFF_ROLES.contains(role)) {
            throw new SecurityException("当前角色没有 AI 分析权限");
        }

        DateRange range = normalizeRange(request);
        SysUser currentUser = userService.getById(userId);
        if (currentUser == null || !role.equals(currentUser.getRole())) {
            throw new SecurityException("账号权限已发生变化，请重新登录");
        }
        // AI management reports intentionally use the complete campus-wide business dataset.
        // Access to this endpoint remains restricted to authenticated staff roles above.
        Scope scope = new Scope(role, List.of(), List.of(), true);

        List<DormCheckRecord> records = loadCheckRecords(scope, range);
        List<DormHazard> hazards = loadHazards(scope, range);
        List<DormHazard> openHazards = loadOpenHazards(scope);
        List<DormLeave> approvedLeaves = loadApprovedLeaves(scope, range);

        Map<String, Object> snapshot = buildSnapshot(records, hazards, openHazards, approvedLeaves);
        List<Map<String, Object>> dailyTrend = buildDailyTrend(records, range);
        List<Map<String, Object>> riskRooms = buildRiskRooms(records);
        List<Map<String, Object>> anomalyRecords = buildAnomalyRecords(records);

        String question = normalizeQuestion(request == null ? null : request.getQuestion());
        boolean english = request != null && "en".equalsIgnoreCase(request.getLanguage());
        String report = buildRuleReport(snapshot, riskRooms, anomalyRecords, english);
        boolean aiAvailable = false;
        String provider = "rule-engine";

        if (aiEnabled && aiApiKey != null && !aiApiKey.isBlank()) {
            try {
                report = requestAiReport(snapshot, dailyTrend, riskRooms, anomalyRecords, range, question, english);
                aiAvailable = true;
                provider = aiModel;
            } catch (Exception e) {
                log.warn("AI provider unavailable; falling back to rule report: {}", e.getClass().getSimpleName());
            }
        }

        AiAnalysisResponse response = new AiAnalysisResponse();
        response.setStartDate(range.startDate());
        response.setEndDate(range.endDate());
        response.setGeneratedAt(LocalDateTime.now());
        response.setAiAvailable(aiAvailable);
        response.setProvider(provider);
        response.setReport(report);
        response.setSnapshot(snapshot);
        response.setDailyTrend(dailyTrend);
        response.setRiskRooms(riskRooms);
        response.setAnomalyRecords(anomalyRecords);
        return response;
    }

    private DateRange normalizeRange(AiAnalysisRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = request != null && request.getEndDate() != null ? request.getEndDate() : today;
        LocalDate startDate = request != null && request.getStartDate() != null
                ? request.getStartDate() : endDate.minusDays(6);

        if (endDate.isAfter(today)) {
            throw new IllegalArgumentException("结束日期不能晚于今天");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > 90) {
            throw new IllegalArgumentException("单次分析的数据范围不能超过90天");
        }
        return new DateRange(startDate, endDate);
    }

    private String normalizeQuestion(String question) {
        if (question == null) return "";
        String normalized = question.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("问题长度不能超过500个字符");
        }
        return normalized;
    }

    private Scope buildScope(SysUser currentUser, String role) {
        if ("ADMIN".equals(role)) {
            return new Scope(role, List.of(), List.of(), true);
        }

        if ("COUNSELOR".equals(role)) {
            if (currentUser.getCollege() == null || currentUser.getCollege().isBlank()
                    || currentUser.getClassName() == null || currentUser.getClassName().isBlank()) {
                return new Scope(role, List.of(), List.of(), false);
            }
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getRole, Arrays.asList("STUDENT", "DORM_LEADER"))
                    .eq(SysUser::getCollege, currentUser.getCollege())
                    .eq(SysUser::getClassName, currentUser.getClassName());
            if (currentUser.getGrade() != null && !currentUser.getGrade().isBlank()) {
                wrapper.eq(SysUser::getGrade, currentUser.getGrade());
            }
            List<Long> studentIds = userService.list(wrapper).stream().map(SysUser::getId).toList();
            return new Scope(role, studentIds, List.of(), false);
        }

        if (currentUser.getBuilding() == null || currentUser.getBuilding().isBlank()) {
            return new Scope(role, List.of(), List.of(), false);
        }
        List<Long> roomIds = roomService.list(new LambdaQueryWrapper<DormRoom>()
                        .eq(DormRoom::getBuilding, currentUser.getBuilding()))
                .stream().map(DormRoom::getId).toList();
        List<Long> studentIds = userService.list(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getBuilding, currentUser.getBuilding())
                        .in(SysUser::getRole, Arrays.asList("STUDENT", "DORM_LEADER")))
                .stream().map(SysUser::getId).toList();
        return new Scope(role, studentIds, roomIds, false);
    }

    private List<DormCheckRecord> loadCheckRecords(Scope scope, DateRange range) {
        LambdaQueryWrapper<DormCheckRecord> wrapper = new LambdaQueryWrapper<DormCheckRecord>()
                .between(DormCheckRecord::getCheckDate, range.startDate(), range.endDate());
        applyCheckScope(wrapper, scope);
        return checkRecordService.list(wrapper);
    }

    private List<DormHazard> loadHazards(Scope scope, DateRange range) {
        LambdaQueryWrapper<DormHazard> wrapper = new LambdaQueryWrapper<DormHazard>()
                .between(DormHazard::getCreateTime, range.startDate().atStartOfDay(), range.endDate().atTime(LocalTime.MAX));
        applyHazardScope(wrapper, scope);
        return hazardService.list(wrapper);
    }

    private List<DormHazard> loadOpenHazards(Scope scope) {
        LambdaQueryWrapper<DormHazard> wrapper = new LambdaQueryWrapper<DormHazard>()
                .in(DormHazard::getStatus, OPEN_HAZARD_STATUSES);
        applyHazardScope(wrapper, scope);
        return hazardService.list(wrapper);
    }

    private List<DormLeave> loadApprovedLeaves(Scope scope, DateRange range) {
        LambdaQueryWrapper<DormLeave> wrapper = new LambdaQueryWrapper<DormLeave>()
                .eq(DormLeave::getStatus, "APPROVED")
                .le(DormLeave::getStartTime, range.endDate().atTime(LocalTime.MAX))
                .ge(DormLeave::getEndTime, range.startDate().atStartOfDay());
        if (!scope.admin()) {
            if (scope.studentIds().isEmpty()) {
                wrapper.eq(DormLeave::getId, -1L);
            } else {
                wrapper.in(DormLeave::getStudentId, scope.studentIds());
            }
        }
        return leaveService.list(wrapper);
    }

    private void applyCheckScope(LambdaQueryWrapper<DormCheckRecord> wrapper, Scope scope) {
        if (scope.admin()) return;
        if ("DORM_MANAGER".equals(scope.role())) {
            if (scope.roomIds().isEmpty()) wrapper.eq(DormCheckRecord::getId, -1L);
            else wrapper.in(DormCheckRecord::getRoomId, scope.roomIds());
        } else {
            if (scope.studentIds().isEmpty()) wrapper.eq(DormCheckRecord::getId, -1L);
            else wrapper.in(DormCheckRecord::getStudentId, scope.studentIds());
        }
    }

    private void applyHazardScope(LambdaQueryWrapper<DormHazard> wrapper, Scope scope) {
        if (scope.admin()) return;
        if ("DORM_MANAGER".equals(scope.role())) {
            if (scope.roomIds().isEmpty()) wrapper.eq(DormHazard::getId, -1L);
            else wrapper.in(DormHazard::getRoomId, scope.roomIds());
        } else {
            if (scope.studentIds().isEmpty()) wrapper.eq(DormHazard::getId, -1L);
            else wrapper.in(DormHazard::getStudentId, scope.studentIds());
        }
    }

    private Map<String, Object> buildSnapshot(List<DormCheckRecord> records,
                                              List<DormHazard> hazards,
                                              List<DormHazard> openHazards,
                                              List<DormLeave> approvedLeaves) {
        long inRoom = countStatus(records, "IN_ROOM");
        long leave = countStatus(records, "LEAVE");
        long late = countStatus(records, "LATE");
        long absent = countStatus(records, "ABSENT");
        long total = records.size();
        BigDecimal normalRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((inRoom + leave) * 100.0 / total).setScale(1, RoundingMode.HALF_UP);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalRecords", total);
        snapshot.put("inRoom", inRoom);
        snapshot.put("leave", leave);
        snapshot.put("late", late);
        snapshot.put("absent", absent);
        snapshot.put("normalRate", normalRate);
        snapshot.put("newHazards", hazards.size());
        snapshot.put("openHazards", openHazards.size());
        snapshot.put("approvedLeaves", approvedLeaves.size());
        return snapshot;
    }

    private long countStatus(List<DormCheckRecord> records, String status) {
        return records.stream().filter(item -> status.equals(item.getStatus())).count();
    }

    private List<Map<String, Object>> buildDailyTrend(List<DormCheckRecord> records, DateRange range) {
        Map<LocalDate, List<DormCheckRecord>> byDate = new HashMap<>();
        records.stream().filter(item -> item.getCheckDate() != null)
                .forEach(item -> byDate.computeIfAbsent(item.getCheckDate(), ignored -> new ArrayList<>()).add(item));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            List<DormCheckRecord> dayRecords = byDate.getOrDefault(date, List.of());
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("inRoom", countStatus(dayRecords, "IN_ROOM"));
            day.put("leave", countStatus(dayRecords, "LEAVE"));
            day.put("late", countStatus(dayRecords, "LATE"));
            day.put("absent", countStatus(dayRecords, "ABSENT"));
            trend.add(day);
        }
        return trend;
    }

    private List<Map<String, Object>> buildRiskRooms(List<DormCheckRecord> records) {
        Map<Long, RoomRisk> risks = new HashMap<>();
        for (DormCheckRecord record : records) {
            if (record.getRoomId() == null) continue;
            RoomRisk risk = risks.computeIfAbsent(record.getRoomId(), ignored -> new RoomRisk());
            if ("LATE".equals(record.getStatus())) risk.late++;
            if ("ABSENT".equals(record.getStatus())) risk.absent++;
        }

        return risks.entrySet().stream()
                .filter(entry -> entry.getValue().total() > 0)
                .sorted(Comparator.comparingInt((Map.Entry<Long, RoomRisk> entry) -> entry.getValue().score()).reversed())
                .limit(5)
                .map(entry -> {
                    DormRoom room = roomService.getById(entry.getKey());
                    RoomRisk risk = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("roomId", entry.getKey());
                    item.put("roomName", room == null ? "未知宿舍" : room.getBuilding() + " · " + room.getRoomNumber());
                    item.put("late", risk.late);
                    item.put("absent", risk.absent);
                    item.put("anomalyCount", risk.total());
                    item.put("riskLevel", risk.level());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> buildAnomalyRecords(List<DormCheckRecord> records) {
        List<DormCheckRecord> anomalies = records.stream()
                .filter(record -> "LATE".equals(record.getStatus()) || "ABSENT".equals(record.getStatus()))
                .sorted(Comparator.comparing(DormCheckRecord::getCheckDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DormCheckRecord::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<Long, SysUser> students = new HashMap<>();
        List<Long> studentIds = anomalies.stream().map(DormCheckRecord::getStudentId)
                .filter(id -> id != null).distinct().toList();
        if (!studentIds.isEmpty()) {
            userService.listByIds(studentIds).forEach(user -> students.put(user.getId(), user));
        }

        Map<Long, DormRoom> rooms = new HashMap<>();
        List<Long> roomIds = anomalies.stream().map(DormCheckRecord::getRoomId)
                .filter(id -> id != null).distinct().toList();
        if (!roomIds.isEmpty()) {
            roomService.listByIds(roomIds).forEach(room -> rooms.put(room.getId(), room));
        }

        return anomalies.stream().map(record -> {
            SysUser student = students.get(record.getStudentId());
            DormRoom room = rooms.get(record.getRoomId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recordId", record.getId());
            item.put("date", record.getCheckDate());
            item.put("status", record.getStatus());
            item.put("studentId", record.getStudentId());
            item.put("studentName", student == null ? "未知学生" : student.getRealName());
            item.put("studentNumber", student == null ? "" : student.getUsername());
            item.put("roomName", room == null ? "未知宿舍" : room.getBuilding() + " · " + room.getRoomNumber());
            item.put("remark", record.getRemark());
            return item;
        }).toList();
    }

    private String requestAiReport(Map<String, Object> snapshot,
                                   List<Map<String, Object>> dailyTrend,
                                   List<Map<String, Object>> riskRooms,
                                   List<Map<String, Object>> anomalyRecords,
                                   DateRange range,
                                   String question,
                                   boolean english) throws Exception {
        Map<String, Object> scopedData = new LinkedHashMap<>();
        scopedData.put("startDate", range.startDate());
        scopedData.put("endDate", range.endDate());
        scopedData.put("summary", snapshot);
        scopedData.put("dailyTrend", dailyTrend);
        scopedData.put("riskRooms", riskRooms);
        scopedData.put("anomalyRecords", anomalyRecords);

        String languageInstruction = english ? "Reply in English." : "请使用简体中文回答。";
        String systemPrompt = "你是高校宿舍夜查管理数据分析助手。仅依据业务后端按当前用户权限范围提供的数据进行分析，"
                + "可以准确引用异常记录中已提供的姓名、学号、日期、宿舍和状态，但不得猜测或补充未提供的个人信息，"
                + "不得输出电话、邮箱等无关信息，也不得声称执行了数据库查询或管理操作。"
                + "报告必须包含：总体情况、异常趋势、重点风险、处理建议四部分。不要使用Markdown表格。"
                + languageInstruction;
        String userPrompt = "以下数据已由业务后端按当前用户权限过滤；anomalyRecords 是可直接回答谁晚归或缺勤的真实异常明细：\n"
                + objectMapper.writeValueAsString(scopedData)
                + (question.isBlank() ? "\n请生成管理分析报告。" : "\n用户问题：" + question);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)));

        String normalizedBaseUrl = aiBaseUrl.endsWith("/")
                ? aiBaseUrl.substring(0, aiBaseUrl.length() - 1) : aiBaseUrl;
        RestClient client = RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("stream", false);
        body.put("thinking", Map.of("type", "disabled"));
        body.put("temperature", 0.2);
        body.put("max_tokens", 1400);

        JsonNode response = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String content = response == null ? null
                : response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI provider returned an empty response");
        }
        return content.trim();
    }

    private String buildRuleReport(Map<String, Object> snapshot,
                                   List<Map<String, Object>> riskRooms,
                                   List<Map<String, Object>> anomalyRecords,
                                   boolean english) {
        long total = ((Number) snapshot.get("totalRecords")).longValue();
        long late = ((Number) snapshot.get("late")).longValue();
        long absent = ((Number) snapshot.get("absent")).longValue();
        long openHazards = ((Number) snapshot.get("openHazards")).longValue();
        Object normalRate = snapshot.get("normalRate");

        if (english) {
            return "Overview\n" + total + " inspection records were included. The normal/leave rate was " + normalRate + "%.\n\n"
                    + "Anomalies\nLate: " + late + "; absent: " + absent + ".\n\n"
                    + "Key risks\n" + riskRooms.size() + " rooms require attention; " + openHazards + " hazards remain open.\n\n"
                    + "Recommendations\nVerify repeated anomalies, close open hazards by priority, and review changes after the next inspection."
                    + "\n\n[Rule-based summary: configure AI_ENABLED and AI_API_KEY on the server for model analysis.]";
        }
        String anomalyPeople = anomalyRecords.isEmpty() ? "无"
                : anomalyRecords.stream()
                .map(item -> item.get("date") + " " + item.get("studentName") + "（" + item.get("studentNumber")
                        + "，" + item.get("roomName") + "，" + ("LATE".equals(item.get("status")) ? "晚归" : "缺勤") + "）")
                .distinct().reduce((left, right) -> left + "；" + right).orElse("无");
        return "总体情况\n本次纳入 " + total + " 条查寝记录，正常及请假占比为 " + normalRate + "% 。\n\n"
                + "异常趋势\n晚归 " + late + " 次，缺勤 " + absent + " 次。异常人员：" + anomalyPeople + "。\n\n"
                + "重点风险\n当前有 " + riskRooms.size() + " 个宿舍需要重点关注，另有 " + openHazards + " 项隐患尚未闭环。\n\n"
                + "处理建议\n优先核实重复异常记录，按风险级别处理未闭环隐患，并在下一次查寝后复核变化。"
                + "\n\n【当前为规则分析；在服务器配置 AI_ENABLED 和 AI_API_KEY 后将启用模型分析。】";
    }

    private record Scope(String role, List<Long> studentIds, List<Long> roomIds, boolean admin) {}
    private record DateRange(LocalDate startDate, LocalDate endDate) {}

    private static class RoomRisk {
        private int late;
        private int absent;

        private int total() { return late + absent; }
        private int score() { return late + absent * 3; }
        private String level() {
            if (absent >= 2 || total() >= 4) return "HIGH";
            if (absent >= 1 || total() >= 2) return "MEDIUM";
            return "LOW";
        }
    }
}
