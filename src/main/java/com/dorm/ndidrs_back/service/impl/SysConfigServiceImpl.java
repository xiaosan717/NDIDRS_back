package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.SysConfig;
import com.dorm.ndidrs_back.mapper.SysConfigMapper;
import com.dorm.ndidrs_back.service.SysConfigService;
import org.springframework.stereotype.Service;

@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {
}