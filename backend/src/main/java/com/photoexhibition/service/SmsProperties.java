package com.photoexhibition.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sms.aliyun")
@Data
public class SmsProperties {
    private boolean enabled = false;
    private boolean mockEnabled = true;
    private String endpoint = "https://dysmsapi.aliyuncs.com/";
    private String regionId = "cn-hangzhou";
    private String accessKeyId;
    private String accessKeySecret;
    private String signName;
    private String templateCode;
    private String templateParamName = "code";
    private String sdkAppId;
    private int codeExpireMinutes = 5;
}
