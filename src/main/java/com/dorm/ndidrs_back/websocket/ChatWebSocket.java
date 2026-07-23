package com.dorm.ndidrs_back.websocket;

import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.entity.ChatMessage;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.ChatMessageService;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/ws/chat/{roomId}")
public class ChatWebSocket {

    private static final Map<String, CopyOnWriteArraySet<SessionInfo>> roomSessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Spring beans via static injection (JSR-356 creates new instances per connection)
    private static JwtUtils jwtUtilsStatic;
    private static ChatMessageService chatMessageServiceStatic;
    private static SysUserService userServiceStatic;
    private static DormRoomService dormRoomServiceStatic;

    @Autowired private JwtUtils jwtUtils;
    @Autowired private ChatMessageService chatMessageService;
    @Autowired private SysUserService userService;
    @Autowired private DormRoomService dormRoomService;

    @PostConstruct
    public void init() {
        jwtUtilsStatic = jwtUtils;
        chatMessageServiceStatic = chatMessageService;
        userServiceStatic = userService;
        dormRoomServiceStatic = dormRoomService;
    }

    private Session session;
    private String roomId;
    private Long userId;
    private String realName;
    private String avatar;

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        this.session = session;
        this.roomId = roomId;

        // JWT 鉴权：从 query param 获取 token
        Map<String, java.util.List<String>> params = session.getRequestParameterMap();
        String token = null;
        if (params.containsKey("token") && !params.get("token").isEmpty()) {
            token = params.get("token").get(0);
        }
        if (token == null || token.isBlank()) {
            closeWithReason(session, "未提供认证Token");
            return;
        }

        try {
            Claims claims = jwtUtilsStatic.parseToken(token);
            this.userId = ((Number) claims.get("userId")).longValue();
            String role = (String) claims.get("role");
            // 仅允许学生和宿舍长使用宿舍聊天
            if (!"STUDENT".equals(role) && !"DORM_LEADER".equals(role)) {
                closeWithReason(session, "当前角色无权使用宿舍聊天");
                return;
            }
        } catch (Exception e) {
            closeWithReason(session, "Token无效或已过期");
            return;
        }

        // 验证用户是否属于该宿舍
        SysUser user = userServiceStatic.getById(userId);
        if (user == null) {
            closeWithReason(session, "用户不存在");
            return;
        }
        this.realName = user.getRealName() != null ? user.getRealName() : user.getUsername();
        this.avatar = user.getAvatar();

        // 查找对应宿舍
        DormRoom room = findRoomByRoomIdStr(roomId, user);
        if (room == null) {
            closeWithReason(session, "您不属于该宿舍群聊");
            return;
        }

        SessionInfo info = new SessionInfo(session, userId, realName, avatar);
        CopyOnWriteArraySet<SessionInfo> sessions = roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>());
        sessions.add(info);

        // 发送加入通知
        try {
            ObjectNode joinMsg = objectMapper.createObjectNode();
            joinMsg.put("type", "system");
            joinMsg.put("content", realName + " 加入了群聊");
            joinMsg.put("time", java.time.LocalDateTime.now().format(timeFormatter));
            joinMsg.put("onlineCount", sessions.size());
            broadcast(roomId, objectMapper.writeValueAsString(joinMsg));
        } catch (Exception ignored) {}
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            ObjectNode json = objectMapper.readValue(message, ObjectNode.class);
            String type = json.has("type") ? json.get("type").asText() : "TEXT";
            String content = json.has("content") ? json.get("content").asText() : null;

            if (content == null || content.isBlank()) return;

            // 消息类型白名单
            String msgType;
            switch (type.toUpperCase()) {
                case "IMAGE": msgType = "IMAGE"; break;
                case "VIDEO": msgType = "VIDEO"; break;
                case "EMOJI": msgType = "EMOJI"; break;
                default: msgType = "TEXT"; break;
            }

            // 持久化到数据库
            ChatMessage saved = chatMessageServiceStatic.saveMessage(
                    parseRoomId(roomId), userId, realName, avatar, msgType, content);

            // 构造广播消息
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", msgType);
            response.put("sender", realName);
            response.put("senderId", userId);
            response.put("senderAvatar", avatar != null ? avatar : "");
            response.put("content", content);
            response.put("id", saved.getId());
            response.put("time", saved.getCreateTime().format(timeFormatter));

            broadcast(roomId, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            System.err.println("处理聊天消息失败: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose() {
        CopyOnWriteArraySet<SessionInfo> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.removeIf(info -> info.session == this.session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
            // 发送离开通知
            try {
                ObjectNode leaveMsg = objectMapper.createObjectNode();
                leaveMsg.put("type", "system");
                leaveMsg.put("content", realName + " 离开了群聊");
                leaveMsg.put("time", java.time.LocalDateTime.now().format(timeFormatter));
                leaveMsg.put("onlineCount", sessions.size());
                broadcast(roomId, objectMapper.writeValueAsString(leaveMsg));
            } catch (Exception ignored) {}
        }
    }

    @OnError
    public void onError(Throwable error) {
        System.err.println("WebSocket错误: " + error.getMessage());
        onClose();
    }

    private void broadcast(String roomId, String message) {
        CopyOnWriteArraySet<SessionInfo> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            for (SessionInfo info : sessions) {
                try {
                    if (info.session.isOpen()) {
                        info.session.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    System.err.println("发送消息失败: " + e.getMessage());
                }
            }
        }
    }

    private void closeWithReason(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException ignored) {}
    }

    private DormRoom findRoomByRoomIdStr(String roomIdStr, SysUser user) {
        try {
            // roomId 可以是 dorm_room 表的 id 或 "building_roomNumber" 格式
            if (roomIdStr.matches("\\d+")) {
                DormRoom room = dormRoomServiceStatic.getById(Long.parseLong(roomIdStr));
                if (room != null && user.getBuilding() != null && user.getRoom() != null
                        && user.getBuilding().equals(room.getBuilding()) && user.getRoom().equals(room.getRoomNumber())) {
                    return room;
                }
                return null;
            }
            // 格式: building_roomNumber
            String[] parts = roomIdStr.split("_", 2);
            if (parts.length == 2 && user.getBuilding() != null && user.getRoom() != null
                    && user.getBuilding().equals(parts[0]) && user.getRoom().equals(parts[1])) {
                return null; // 仅允许数字ID
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseRoomId(String roomIdStr) {
        try {
            return Long.parseLong(roomIdStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static class SessionInfo {
        final Session session;
        final Long userId;
        final String realName;
        final String avatar;

        SessionInfo(Session session, Long userId, String realName, String avatar) {
            this.session = session;
            this.userId = userId;
            this.realName = realName;
            this.avatar = avatar;
        }
    }
}
