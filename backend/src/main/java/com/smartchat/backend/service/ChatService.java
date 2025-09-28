/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service;

import com.smartchat.backend.dto.RecentChatResponse;
import com.smartchat.backend.model.ChatMessage;

import java.util.List;

public interface ChatService {
    List<RecentChatResponse> getRecentChats(Long userId);

    ChatMessage saveAndSend(ChatMessage msg);

    ChatMessage updateStatus(Long messageId, String status);

    ChatMessage deleteMessage(Long id, Long userId);
}
