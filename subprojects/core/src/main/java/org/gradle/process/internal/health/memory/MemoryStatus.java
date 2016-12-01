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

package org.gradle.process.internal.health.memory;

import org.gradle.api.Nullable;

public class MemoryStatus {
    private final long maxMemory;
    private final long committedMemory;
    private final Long totalPhysicalMemory;
    private final Long freePhysicalMemory;

    public MemoryStatus(long maxMemory, long committedMemory, @Nullable Long totalPhysicalMemory, @Nullable Long freePhysicalMemory) {
        this.maxMemory = maxMemory;
        this.committedMemory = committedMemory;
        this.totalPhysicalMemory = totalPhysicalMemory;
        this.freePhysicalMemory = freePhysicalMemory;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public long getCommittedMemory() {
        return committedMemory;
    }

    @Nullable
    public Long getTotalPhysicalMemory() {
        return totalPhysicalMemory;
    }

    @Nullable
    public Long getFreePhysicalMemory() {
        return freePhysicalMemory;
    }

    public String toString() {
        return "{ Max: " + maxMemory + ", Committed: " + committedMemory + ", TotalPhysical: " + totalPhysicalMemory + " FreePhysical: " + freePhysicalMemory + " }";
    }
}
