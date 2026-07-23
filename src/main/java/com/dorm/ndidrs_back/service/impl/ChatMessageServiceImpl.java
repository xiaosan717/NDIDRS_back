package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.ChatMessage;
import com.dorm.ndidrs_back.mapper.ChatMessageMapper;
import com.dorm.ndidrs_back.service.ChatMessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Override
    public List<ChatMessage> getHistory(String roomId, Long beforeId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRoomId, roomId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + Math.min(limit, 100));
        if (beforeId != null && beforeId > 0) {
            wrapper.lt(ChatMessage::getId, beforeId);
        }
        List<ChatMessage> list = list(wrapper);
        java.util.Collections.reverse(list);
        return list;
    }

    @Override
    public ChatMessage saveMessage(String roomId, Long senderId, String senderName, String senderAvatar, String msgType, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRoomId(roomId);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setSenderAvatar(senderAvatar);
        msg.setMsgType(msgType);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        save(msg);
        return msg;
    }
}
