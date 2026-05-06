package com.vibedev.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cas")
public class CasConfig {

    private String serverUrl;
    private String loginUrl;
    private String serviceValidateUrl;
    private String service;

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getLoginUrl() { return loginUrl; }
    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }
    public String getServiceValidateUrl() { return serviceValidateUrl; }
    public void setServiceValidateUrl(String serviceValidateUrl) { this.serviceValidateUrl = serviceValidateUrl; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
}
