package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.DormLeave;
import com.dorm.ndidrs_back.mapper.DormLeaveMapper;
import com.dorm.ndidrs_back.service.DormLeaveService;
import org.springframework.stereotype.Service;

@Service
public class DormLeaveServiceImpl extends ServiceImpl<DormLeaveMapper, DormLeave> implements DormLeaveService {
}