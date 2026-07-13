package com.transfer.adapter;

import com.transfer.enums.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(String receiver, String title, String content) {
    }
}
