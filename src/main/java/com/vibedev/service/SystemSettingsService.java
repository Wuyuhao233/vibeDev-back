package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.entity.SystemConfig;
import com.vibedev.repository.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SystemSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingsService.class);

    private final SystemConfigRepository configRepo;

    public SystemSettingsService(SystemConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    public Map<String, String> getAll() {
        var configs = configRepo.findAll();
        Map<String, String> result = new LinkedHashMap<>();
        for (var c : configs) {
            result.put(c.getConfigKey(), c.getConfigValue());
        }
        return result;
    }

    @Transactional
    public void update(String key, String value) {
        var config = configRepo.findByConfigKey(key);
        if (config.isPresent()) {
            config.get().setConfigValue(value);
            config.get().setUpdatedAt(java.time.Instant.now());
            configRepo.save(config.get());
        } else {
            var newConfig = new SystemConfig();
            newConfig.setId(UUID.randomUUID().toString());
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            configRepo.save(newConfig);
        }
    }

    public String getValue(String key) {
        return configRepo.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    public String getValue(String key, String defaultValue) {
        return configRepo.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }
}
