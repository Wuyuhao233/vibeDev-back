package com.vibedev.repository;

import com.vibedev.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(String recipientId, boolean isRead, Pageable pageable);

    int countByRecipientIdAndIsReadFalse(String recipientId);
}
