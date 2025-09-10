package com.smartchat.backend.controller;

import com.smartchat.backend.model.ChatMessage;
import com.smartchat.backend.model.User;
import com.smartchat.backend.repository.ChatMessageRepository;
import com.smartchat.backend.repository.UserRepository;
import com.smartchat.backend.service.ChatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller responsible for handling chat-related operations,
 * including fetching contacts, retrieving chat history, and sending messages.
 *
 * <p>Key features:
 * <ul>
 *   <li>Provides an endpoint to fetch all registered contacts (users).</li>
 *   <li>Retrieves chat history from Redis cache when available, falling back to database if not.</li>
 *   <li>Supports sending messages which are stored in both the database and Redis cache.</li>
 *   <li>Timestamps for messages are automatically managed at entity level via {@code @PrePersist}.</li>
 * </ul>
 *
 * <p>Base URL: {@code /api}</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatRestController {

    private final UserRepository userRepo;
    private final ChatMessageRepository chatRepo;
    private final ChatCacheService chatCache;

    /**
     * Fetch all available contacts (users).
     *
     * @return list of all registered users in the system
     */
    @GetMapping("/contacts")
    public List<User> getContacts() {
        return userRepo.findAll();
    }

    /**
     * Retrieve the latest chat history between the current user and a contact.
     * First checks the Redis cache for recent messages; if none found,
     * falls back to the database (last 30 messages).
     *
     * @param contactId the ID of the contact user
     * @param userId    the ID of the current user requesting the history
     * @return list of chat messages between the two users
     */
    @GetMapping("/chats/{contactId}")
    public List<ChatMessage> getChatHistory(@PathVariable Long contactId, @RequestParam Long userId) {
        List<ChatMessage> cached = chatCache.getLastMessages(userId, contactId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<ChatMessage> dbMessages =
                chatRepo.findTop30BySenderIdAndReceiverIdOrderByTimestampDesc(userId, contactId);

        dbMessages.forEach(chatCache::cacheMessage);
        return dbMessages;
    }

    /**
     * Send a new chat message from one user to another.
     * The message is saved in the database and cached in Redis.
     * Timestamp is automatically set via @PrePersist in the entity.
     *
     * @param msg the chat message object (senderId, receiverId, message, emoji)
     * @return the persisted chat message with generated ID and timestamp
     */
    @PostMapping("/chats/send")
    public ChatMessage sendMessage(@RequestBody ChatMessage msg) {
        ChatMessage saved = chatRepo.save(msg);
        chatCache.cacheMessage(saved);
        return saved;
    }
}
