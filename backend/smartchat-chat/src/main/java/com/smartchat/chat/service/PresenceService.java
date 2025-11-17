/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.chat.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PresenceService (Redis-backed)
 * <p>
 * - Maintains Redis TTL keys for online presence
 * - Keeps local cache for fast reads and metrics
 * - Exposes getFullSnapshot() used by controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PREFIX = "presence:user:";
    private static final String LAST_SEEN_KEY = "presence:lastSeen";

    @Value("${presence.ttl-seconds:40}")
    private long ttlSeconds;

    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;

    // local caches
    private final Map<String, Boolean> presenceMap = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSeenMap = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastUpdateMap = new ConcurrentHashMap<>();

    // metrics
    private final AtomicInteger activeUsers = new AtomicInteger(0);
    private final AtomicInteger snapshotSize = new AtomicInteger(0);
    private Counter presenceUpdates;

    @PostConstruct
    public void initMetrics() {
        Gauge.builder("smartchat_active_users_total", activeUsers, AtomicInteger::get)
                .description("Number of currently online SmartChat users")
                .register(meterRegistry);

        Gauge.builder("smartchat_presence_snapshot_size", snapshotSize, AtomicInteger::get)
                .description("Number of users in the latest presence snapshot")
                .register(meterRegistry);

        presenceUpdates = Counter.builder("smartchat_presence_updates_total")
                .description("Total number of presence state changes handled (online/offline)")
                .register(meterRegistry);

        log.info("[PresenceService] ✅ Presence metrics initialized (TTL={}s)", ttlSeconds);
    }

    /**
     * Mark user online: set Redis key with TTL and update lastSeen hash.
     */
    public synchronized void markOnline(String userId) {
        if (shouldDebounce(userId, true)) return;

        try {
            redisTemplate.opsForValue().set(PREFIX + userId, "online", ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForHash().put(LAST_SEEN_KEY, userId, Instant.now().toEpochMilli());

            presenceMap.put(userId, true);
            lastSeenMap.put(userId, Instant.now());
            lastUpdateMap.put(userId, Instant.now());
            presenceUpdates.increment();
            updateActiveUsers();

            log.debug("[PresenceService] 🟢 User {} ONLINE (TTL={}s)", userId, ttlSeconds);
        } catch (Exception e) {
            log.error("[PresenceService] ❌ markOnline failed for {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Refresh TTL when client pings
     */
    public void refreshTTL(String userId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + userId))) {
                redisTemplate.expire(PREFIX + userId, ttlSeconds, TimeUnit.SECONDS);
                redisTemplate.opsForHash().put(LAST_SEEN_KEY, userId, Instant.now().toEpochMilli());
                lastSeenMap.put(userId, Instant.now());
                log.trace("[PresenceService] 🔁 TTL refreshed for {}", userId);
            } else {
                // If key expired, treat as reconnect
                markOnline(userId);
            }
        } catch (Exception e) {
            log.error("[PresenceService] ❌ refreshTTL failed for {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Mark user offline: delete Redis key and update lastSeen
     */
    public synchronized void markOffline(String userId) {
        if (shouldDebounce(userId, false)) return;

        try {
            redisTemplate.delete(PREFIX + userId);
            redisTemplate.opsForHash().put(LAST_SEEN_KEY, userId, Instant.now().toEpochMilli());

            presenceMap.put(userId, false);
            lastSeenMap.put(userId, Instant.now());
            lastUpdateMap.put(userId, Instant.now());
            presenceUpdates.increment();
            updateActiveUsers();

            log.debug("[PresenceService] 🔴 User {} OFFLINE", userId);
        } catch (Exception e) {
            log.error("[PresenceService] ❌ markOffline failed for {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Build full snapshot from Redis keys (safe and idempotent)
     */
    public Map<String, Map<String, Object>> getFullSnapshot() {
        var snapshot = new ConcurrentHashMap<String, Map<String, Object>>();
        try {
            Set<String> keys = redisTemplate.keys(PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String uid = key.replace(PREFIX, "");
                    Object lastSeenMillis = redisTemplate.opsForHash().get(LAST_SEEN_KEY, uid);
                    snapshot.put(uid, Map.of(
                            "online", true,
                            "lastSeen", lastSeenMillis != null ? lastSeenMillis : null
                    ));
                }
            }
            snapshotSize.set(snapshot.size());
            log.trace("[PresenceService] 📡 Snapshot built ({} users)", snapshot.size());
        } catch (Exception e) {
            log.error("[PresenceService] ❌ getFullSnapshot error: {}", e.getMessage(), e);
        }
        return snapshot;
    }

    /**
     * Local quick check
     */
    public boolean isOnline(String userId) {
        try {
            Boolean exists = redisTemplate.hasKey(PREFIX + userId);
            presenceMap.put(userId, Boolean.TRUE.equals(exists));
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("[PresenceService] ❌ isOnline error for {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all cached presence (local cache)
     */
    public Map<String, Boolean> getAll() {
        return new ConcurrentHashMap<>(presenceMap);
    }

    /**
     * Get all online userIds from Redis keys
     */
    public Set<String> getAllOnlineFromRedis() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys == null || keys.isEmpty()) return Set.of();
        Set<String> userIds = new HashSet<>(keys.size());
        for (String key : keys) {
            userIds.add(key.replace(PREFIX, ""));
        }
        return userIds;
    }

    // ======= helpers ========
    private void updateActiveUsers() {
        long count = presenceMap.values().stream().filter(Boolean::booleanValue).count();
        activeUsers.set((int) count);
    }

    private boolean shouldDebounce(String userId, boolean goingOnline) {
        var now = Instant.now();
        var last = lastUpdateMap.get(userId);
        var current = presenceMap.get(userId);
        if (last != null && now.toEpochMilli() - last.toEpochMilli() < 500) return true;
        return current != null && current == goingOnline;
    }
}
