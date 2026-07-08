package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.DormCheckRecord;
import com.dorm.ndidrs_back.mapper.DormCheckRecordMapper;
import com.dorm.ndidrs_back.service.DormCheckRecordService;
import org.springframework.stereotype.Service;

@Service
public class DormCheckRecordServiceImpl extends ServiceImpl<DormCheckRecordMapper, DormCheckRecord> implements DormCheckRecordService {
}