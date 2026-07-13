package com.transfer.adapter;

import com.transfer.enums.NotificationChannel;

public interface NotificationProvider {
    NotificationChannel channel();

    void send(String receiver, String title, String content);
}
