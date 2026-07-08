package com.dorm.ndidrs_back.config;

import com.dorm.ndidrs_back.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        e.printStackTrace();
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            message = e.getClass().getSimpleName() + ": " + e.getStackTrace()[0].toString();
        }
        return Result.error(500, message);
    }
}