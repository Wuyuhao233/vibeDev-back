package com.vibedev.repository;

import com.vibedev.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, String> {

    List<UserNotificationSetting> findByUserId(String userId);

    Optional<UserNotificationSetting> findByUserIdAndEventType(String userId, String eventType);
}
