package com.mcfaas.controlplane.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConfigurationProperties(prefix = "mcfaas.rate")
public class RateLimiter {
    private int maxPerSecond = 1000;
    private volatile long windowStartSecond = Instant.now().getEpochSecond();
    private final AtomicInteger windowCount = new AtomicInteger();

    public boolean allow() {
        long now = Instant.now().getEpochSecond();
        if (now != windowStartSecond) {
            synchronized (this) {
                if (now != windowStartSecond) {
                    windowStartSecond = now;
                    windowCount.set(0);
                }
            }
        }
        return windowCount.incrementAndGet() <= maxPerSecond;
    }

    public int getMaxPerSecond() {
        return maxPerSecond;
    }

    public void setMaxPerSecond(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
    }
}
