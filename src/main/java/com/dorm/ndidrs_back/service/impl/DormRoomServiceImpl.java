package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.mapper.DormRoomMapper;
import com.dorm.ndidrs_back.service.DormRoomService;
import org.springframework.stereotype.Service;

@Service
public class DormRoomServiceImpl extends ServiceImpl<DormRoomMapper, DormRoom> implements DormRoomService {
}