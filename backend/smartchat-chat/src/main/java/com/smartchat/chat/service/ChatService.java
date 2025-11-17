/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.service;

import com.smartchat.chat.dto.RecentChatResponse;
import com.smartchat.chat.model.ChatMessage;

import java.util.List;

public interface ChatService {

    List<RecentChatResponse> getRecentChats(Long userId);

    ChatMessage saveAndSend(ChatMessage msg);

    ChatMessage updateStatus(String messageId, String status);

    ChatMessage deleteMessage(String id, Long userId);

    void broadcastStatus(ChatMessage message);
}
