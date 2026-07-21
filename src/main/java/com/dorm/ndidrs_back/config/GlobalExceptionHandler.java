package com.dorm.ndidrs_back.config;

import com.dorm.ndidrs_back.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled request error, errorId={}", errorId, e);
        return Result.error(500, "服务器内部错误，请稍后重试（错误编号：" + errorId + "）");
    }
}
