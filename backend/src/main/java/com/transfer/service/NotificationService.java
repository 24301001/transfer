package com.transfer.service;

import com.transfer.adapter.NotificationProvider;
import com.transfer.enums.NotificationChannel;
import com.transfer.enums.NotificationStatus;
import com.transfer.model.NotificationRecord;
import com.transfer.model.UserAccount;
import com.transfer.repository.NotificationRecordRepository;
import com.transfer.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final NotificationRecordRepository notificationRecordRepository;
    private final UserAccountRepository userAccountRepository;
    private final Map<NotificationChannel, NotificationProvider> providers = new EnumMap<>(NotificationChannel.class);

    public NotificationService(
            NotificationRecordRepository notificationRecordRepository,
            UserAccountRepository userAccountRepository,
            List<NotificationProvider> notificationProviders
    ) {
        this.notificationRecordRepository = notificationRecordRepository;
        this.userAccountRepository = userAccountRepository;
        notificationProviders.forEach(provider -> providers.put(provider.channel(), provider));
    }

    public NotificationRecord send(Long receiverUserId, NotificationChannel channel, String title, String content) {
        NotificationRecord record = new NotificationRecord();
        record.setReceiverUserId(receiverUserId);
        record.setChannel(channel);
        record.setTitle(title);
        record.setContent(content);
        record.setStatus(NotificationStatus.PENDING);
        record = notificationRecordRepository.save(record);

        try {
            if (channel == NotificationChannel.SYSTEM) {
                record.setStatus(NotificationStatus.SENT);
            } else {
                NotificationProvider provider = providers.get(channel);
                if (provider == null) {
                    throw new IllegalStateException("No notification provider configured for " + channel);
                }
                provider.send(resolveReceiver(receiverUserId, channel), title, content);
                record.setStatus(NotificationStatus.SENT);
            }
            record.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            record.setStatus(NotificationStatus.FAILED);
            record.setFailureReason(ex.getMessage());
        }
        return notificationRecordRepository.save(record);
    }

    private String resolveReceiver(Long receiverUserId, NotificationChannel channel) {
        if (receiverUserId == null) {
            return "";
        }
        return userAccountRepository.findById(receiverUserId)
                .map(user -> channel == NotificationChannel.EMAIL ? nullToEmpty(user.getEmail()) : nullToEmpty(user.getPhone()))
                .orElse("");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
