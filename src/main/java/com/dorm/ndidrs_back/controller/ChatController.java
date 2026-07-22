package com.dorm.ndidrs_back.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Map<String, List<ObjectNode>> roomMessages = new ConcurrentHashMap<>();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @PostMapping("/{roomId}/send")
    public ObjectNode sendMessage(@PathVariable String roomId, @RequestBody ObjectNode request) {
        String sender = request.has("sender") ? request.get("sender").asText() : "unknown";
        String content = request.has("content") ? request.get("content").asText() : "";
        
        if (content == null || content.trim().isEmpty()) {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put("code", 400);
            response.put("message", "消息内容不能为空");
            return response;
        }

        List<ObjectNode> messages = roomMessages.computeIfAbsent(roomId, k -> new ArrayList<>());
        
        ObjectNode message = new ObjectMapper().createObjectNode();
        message.put("type", "chat");
        message.put("sender", sender);
        message.put("content", content);
        message.put("time", LocalDateTime.now().format(formatter));
        message.put("id", System.currentTimeMillis());
        
        messages.add(message);
        if (messages.size() > 100) {
            messages.remove(0);
        }

        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("code", 200);
        response.put("message", "发送成功");
        response.set("data", message);
        return response;
    }

    @GetMapping("/{roomId}/messages")
    public ObjectNode getMessages(@PathVariable String roomId, @RequestParam(defaultValue = "0") long lastId) {
        List<ObjectNode> messages = roomMessages.getOrDefault(roomId, new ArrayList<>());
        
        List<ObjectNode> newMessages = new ArrayList<>();
        for (ObjectNode msg : messages) {
            if (msg.has("id") && msg.get("id").asLong() > lastId) {
                newMessages.add(msg);
            }
        }

        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("code", 200);
        response.put("message", "获取成功");
        response.putPOJO("data", newMessages);
        return response;
    }

    @GetMapping("/{roomId}/clear")
    public ObjectNode clearMessages(@PathVariable String roomId) {
        roomMessages.remove(roomId);
        
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("code", 200);
        response.put("message", "清理成功");
        return response;
    }
}