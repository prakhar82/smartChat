/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.service.cache;

import com.smartchat.backend.model.ChatMessage;

import java.util.List;

/**
 * Service interface for caching chat messages in Redis.
 *
 * <p>This service is responsible for managing temporary chat history
 * to improve performance and reduce database calls.</p>
 *
 * <p>Typical operations:
 * <ul>
 *   <li>Fetch chat history between two users from Redis.</li>
 *   <li>Push new messages into Redis while maintaining a fixed history limit.</li>
 *   <li>Update message statuses (e.g., delivered, read) inside the cache.</li>
 *   <li>Store bulk conversation history for initial population.</li>
 * </ul>
 */
public interface ChatCacheService {

    /**
     * Retrieves the cached chat messages between two users.
     *
     * @param userA The first user's ID
     * @param userB The second user's ID
     * @return List of {@link ChatMessage} objects, or empty list if no cache exists
     */
    List<ChatMessage> get(Long userA, Long userB);

    /**
     * Bulk insert or overwrite the chat history between two users.
     * Used when initially populating cache from the database.
     *
     * @param userA    The first user's ID
     * @param userB    The second user's ID
     * @param messages The full conversation history to store
     */
    void put(Long userA, Long userB, List<ChatMessage> messages);

    /**
     * Push a single new message into the Redis cache while keeping
     * only the most recent messages up to the configured history limit.
     *
     * @param userA   The sender or participant A
     * @param userB   The receiver or participant B
     * @param message The new {@link ChatMessage} to add
     */
    void pushMessageToCache(Long userA, Long userB, ChatMessage message);

    /**
     * Update the status of a specific message in the cache.
     * For example, update its "read" or "delivered" status.
     *
     * <p>Implementation should locate the message by its ID and replace
     * it with the updated version.</p>
     *
     * @param message The updated {@link ChatMessage} with new status
     */
    void updateMessageStatusInCache(ChatMessage message);

    /**
     * Optional: Clear the chat cache between two users completely.
     *
     * @param userA The first user's ID
     * @param userB The second user's ID
     */
    default void evict(Long userA, Long userB) {
        // Default no-op. Implementations may override.
    }
}
