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

package org.gradle.process.internal.worker.child;

import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;

public class WorkerOutputEventListener implements OutputEventListener {
    private WorkerLoggingProtocol workerLoggingProtocol;

    public WorkerOutputEventListener(WorkerLoggingProtocol workerLoggingProtocol) {
        this.workerLoggingProtocol = workerLoggingProtocol;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if(event instanceof LogEvent){
            workerLoggingProtocol.sendOutputEvent((LogEvent)event);
        }
    }
}
