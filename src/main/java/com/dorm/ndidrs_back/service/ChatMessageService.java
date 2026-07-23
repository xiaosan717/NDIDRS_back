package com.dorm.ndidrs_back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dorm.ndidrs_back.entity.ChatMessage;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {
    List<ChatMessage> getHistory(String roomId, Long beforeId, int limit);
    ChatMessage saveMessage(String roomId, Long senderId, String senderName, String senderAvatar, String msgType, String content);
}
