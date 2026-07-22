package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.SysOperationLog;
import com.dorm.ndidrs_back.mapper.SysOperationLogMapper;
import com.dorm.ndidrs_back.service.SysOperationLogService;
import org.springframework.stereotype.Service;

@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog> implements SysOperationLogService {
}