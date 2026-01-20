package com.photoexhibition.service;

/**
 * ONNX配置异常 - 用于标识ONNX运行时配置问题
 */
public class OnnxConfigurationException extends RuntimeException {
    public OnnxConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public OnnxConfigurationException(String message) {
        super(message);
    }
}
