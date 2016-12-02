/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal.daemon;

import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.health.memory.MemoryInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultMemoryResourceManager implements MemoryResourceManager {

    private static final Logger LOGGER = Logging.getLogger(MemoryResourceManager.class);
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024; // 384M
    private static final int MONITOR_CHECK_INITIAL_DELAY_SECONDS = 5;
    private static final int MONITOR_CHECK_INTERVAL_SECONDS = 5;

    private final MemoryInfo memoryInfo;
    private final long memoryThresholdInBytes;
    private final Object lock = new Object();
    private final List<MemoryResourceHolder> holders = new ArrayList<MemoryResourceHolder>();

    public DefaultMemoryResourceManager(ScheduledExecutorService scheduledExecutorService, MemoryInfo memoryInfo, double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.memoryInfo = Preconditions.checkNotNull(memoryInfo);
        this.memoryThresholdInBytes = Math.max(MIN_THRESHOLD_BYTES, (long) (memoryInfo.getTotalPhysicalMemory() * minFreeMemoryPercentage));
        scheduleMonitoringThread(scheduledExecutorService);
    }

    private void scheduleMonitoringThread(ScheduledExecutorService scheduledExecutorService) {
        scheduledExecutorService.scheduleAtFixedRate(new FreeMemoryCheck(), MONITOR_CHECK_INITIAL_DELAY_SECONDS, MONITOR_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void register(MemoryResourceHolder holder) {
        synchronized (lock) {
            holders.add(holder);
        }
    }

    @Override
    public void unregister(MemoryResourceHolder holder) {
        synchronized (lock) {
            holders.remove(holder);
        }
    }

    @Override
    public void requestFreeMemory(long memoryAmountBytes) {
        long claimedFreeMemory = memoryThresholdInBytes + (memoryAmountBytes > 0 ? memoryAmountBytes : 0);
        long freeMemory = memoryInfo.getFreePhysicalMemory();
        doRequestFreeMemory(claimedFreeMemory, freeMemory);
    }

    private void doRequestFreeMemory(long claimedFreeMemory, long freeMemory) {
        long toReleaseMemory = claimedFreeMemory;
        LOGGER.debug("{} memory claimed, {} free", claimedFreeMemory, freeMemory);
        if (freeMemory < claimedFreeMemory) {
            synchronized (lock) {
                for (MemoryResourceHolder holder : holders) {
                    long released = holder.attemptToRelease(toReleaseMemory);
                    toReleaseMemory -= released;
                    freeMemory += released;
                    if (freeMemory >= claimedFreeMemory) {
                        break;
                    }
                }
            }
        }
        LOGGER.debug("{} memory claimed, {} released, {} free", claimedFreeMemory, claimedFreeMemory - toReleaseMemory, freeMemory);
    }

    private class FreeMemoryCheck implements Runnable {
        @Override
        public void run() {
            long freeMemory = memoryInfo.getFreePhysicalMemory();
            if (freeMemory < memoryThresholdInBytes) {
                doRequestFreeMemory(memoryThresholdInBytes, freeMemory);
            }
        }
    }
}
