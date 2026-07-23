package com.dorm.ndidrs_back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dorm.ndidrs_back.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
