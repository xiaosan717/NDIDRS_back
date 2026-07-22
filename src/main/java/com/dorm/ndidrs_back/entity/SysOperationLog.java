package com.dorm.ndidrs_back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    private String operationType;
    private String operationModule;
    private String operationDesc;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String responseResult;
    private String ipAddress;
    private LocalDateTime operationTime;
}