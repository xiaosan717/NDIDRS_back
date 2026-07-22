package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.SysOperationLog;
import com.dorm.ndidrs_back.service.SysOperationLogService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
public class SysOperationLogController {
    private final SysOperationLogService logService;

    public SysOperationLogController(SysOperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public Result<Page<SysOperationLog>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String operationModule,
                                               @RequestParam(required = false) String operationType) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (operationModule != null) wrapper.eq(SysOperationLog::getOperationModule, operationModule);
        if (operationType != null) wrapper.eq(SysOperationLog::getOperationType, operationType);
        wrapper.orderByDesc(SysOperationLog::getOperationTime);
        Page<SysOperationLog> page = logService.page(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(page);
    }
}