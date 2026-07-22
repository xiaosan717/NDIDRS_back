package com.dorm.ndidrs_back.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/ws/chat/{roomId}")
public class ChatWebSocket {

    private static final Map<String, CopyOnWriteArraySet<ChatWebSocket>> roomSessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private Session session;
    private String roomId;

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        this.session = session;
        this.roomId = roomId;
        
        CopyOnWriteArraySet<ChatWebSocket> sessions = roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>());
        sessions.add(this);
        
        System.out.println("WebSocket连接打开: roomId=" + roomId + ", count=" + sessions.size());
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            ObjectNode json = objectMapper.readValue(message, ObjectNode.class);
            String type = json.has("type") ? json.get("type").asText() : null;
            String sender = json.has("sender") ? json.get("sender").asText() : null;
            String content = json.has("content") ? json.get("content").asText() : null;
            
            if ("chat".equals(type) && sender != null && content != null) {
                ObjectNode response = objectMapper.createObjectNode();
                response.put("type", "chat");
                response.put("sender", sender);
                response.put("content", content);
                response.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                
                broadcast(roomId, objectMapper.writeValueAsString(response));
            }
        } catch (Exception e) {
            System.err.println("处理消息失败: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose() {
        CopyOnWriteArraySet<ChatWebSocket> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(this);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        System.out.println("WebSocket连接关闭: roomId=" + roomId);
    }

    @OnError
    public void onError(Throwable error) {
        System.err.println("WebSocket错误: " + error.getMessage());
        onClose();
    }

    private void broadcast(String roomId, String message) {
        CopyOnWriteArraySet<ChatWebSocket> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            for (ChatWebSocket client : sessions) {
                try {
                    client.session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.err.println("发送消息失败: " + e.getMessage());
                }
            }
        }
    }
}