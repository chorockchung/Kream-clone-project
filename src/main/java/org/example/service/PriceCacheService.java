package org.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "current-price:";
    private static final Duration TTL = Duration.ofHours(1);

    public void updateCurrentPrice(Long productSizeId, int price) {
        String key = KEY_PREFIX + productSizeId;
        redisTemplate.opsForValue().set(key, String.valueOf(price), TTL);
    }

    public Optional<Integer> getCurrentPrice(Long productSizeId) {
        String key = KEY_PREFIX + productSizeId;
        String value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value).map(Integer::parseInt);
    }

    public void evict(Long productSizeId) {
        redisTemplate.delete(KEY_PREFIX + productSizeId);
    }
}