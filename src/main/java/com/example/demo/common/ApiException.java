package com.example.demo.common;

import lombok.Getter;

/** 业务异常：由全局异常处理器转换为统一响应体 */
@Getter
public class ApiException extends RuntimeException {
    private final int code;

    public ApiException(String message) {
        this(400, message);
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }
}
