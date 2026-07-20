package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.DormHazard;
import com.dorm.ndidrs_back.mapper.DormHazardMapper;
import com.dorm.ndidrs_back.service.DormHazardService;
import org.springframework.stereotype.Service;

@Service
public class DormHazardServiceImpl extends ServiceImpl<DormHazardMapper, DormHazard> implements DormHazardService {
}