package com.vibedev.service;

import com.vibedev.entity.SystemConfig;
import com.vibedev.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock private SystemConfigRepository configRepo;

    @InjectMocks
    private SystemSettingsService service;

    @Test
    void getAll_returnsMap() {
        var c1 = new SystemConfig();
        c1.setId("1");
        c1.setConfigKey("site.name");
        c1.setConfigValue("vibeDev");

        var c2 = new SystemConfig();
        c2.setId("2");
        c2.setConfigKey("site.description");
        c2.setConfigValue("A forum");

        when(configRepo.findAll()).thenReturn(List.of(c1, c2));

        var result = service.getAll();
        assertEquals("vibeDev", result.get("site.name"));
        assertEquals("A forum", result.get("site.description"));
    }

    @Test
    void update_existingKey_updatesValue() {
        var config = new SystemConfig();
        config.setId("1");
        config.setConfigKey("site.name");
        config.setConfigValue("old");

        when(configRepo.findByConfigKey("site.name")).thenReturn(Optional.of(config));

        service.update("site.name", "new");

        assertEquals("new", config.getConfigValue());
        verify(configRepo).save(config);
    }

    @Test
    void update_newKey_createsConfig() {
        when(configRepo.findByConfigKey("new.key")).thenReturn(Optional.empty());

        service.update("new.key", "value");

        verify(configRepo).save(any(SystemConfig.class));
    }

    @Test
    void getValue_withDefault_returnsDefaultWhenMissing() {
        when(configRepo.findByConfigKey("missing")).thenReturn(Optional.empty());

        var result = service.getValue("missing", "default");
        assertEquals("default", result);
    }
}
