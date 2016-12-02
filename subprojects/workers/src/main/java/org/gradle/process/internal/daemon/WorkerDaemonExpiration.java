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
import org.gradle.api.Transformer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.health.memory.MaximumHeapHelper;
import org.gradle.process.internal.health.memory.MemoryInfo;

import java.util.ArrayList;
import java.util.List;

public class WorkerDaemonExpiration implements MemoryResourceHolder {
    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonExpiration.class);

    private final WorkerDaemonClientsManager clientsManager;
    private final MaximumHeapHelper maximumHeapHelper;

    public WorkerDaemonExpiration(WorkerDaemonClientsManager clientsManager, MemoryInfo memoryInfo) {
        this.clientsManager = Preconditions.checkNotNull(clientsManager);
        this.maximumHeapHelper = new MaximumHeapHelper(Preconditions.checkNotNull(memoryInfo));
    }

    @Override
    public long attemptToRelease(long memoryAmountBytes) throws IllegalArgumentException {
        SimpleMemoryExpirationSelector selector = new SimpleMemoryExpirationSelector(memoryAmountBytes);
        clientsManager.selectIdleClientsToStop(selector);
        return selector.getReleasedBytes();
    }

    /**
     * Simple implementation of memory based expiration.
     *
     * Use the maximum heap size of each daemon, not their actual memory usage.
     * Expire as much daemons as needed to free the requested memory under the threshold.
     */
    private class SimpleMemoryExpirationSelector implements Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>> {

        private final long memoryBytesToRelease;
        private long releasedBytes;

        public SimpleMemoryExpirationSelector(long memoryBytesToRelease) {
            this.memoryBytesToRelease = memoryBytesToRelease;
        }

        @Override
        public List<WorkerDaemonClient> transform(List<WorkerDaemonClient> idleClients) {
            List<WorkerDaemonClient> toExpire = new ArrayList<WorkerDaemonClient>();
            for (WorkerDaemonClient idleClient : idleClients) {
                LOGGER.info("Expire {} to free some system memory", idleClient);
                toExpire.add(idleClient);
                long freed = getMemoryUsage(idleClient);
                releasedBytes += freed;
                if (releasedBytes >= memoryBytesToRelease) {
                    break;
                }
            }
            return toExpire;
        }

        private long getMemoryUsage(WorkerDaemonClient idleClient) {
            return maximumHeapHelper.getMaximumHeapSize(idleClient.getForkOptions().getMaxHeapSize());
        }

        public long getReleasedBytes() {
            return releasedBytes;
        }
    }
}
