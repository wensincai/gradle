/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ResolveContext;

public interface ConfigurationInternal extends ResolveContext, Configuration, DependencyMetaDataProvider {
    enum InternalState {UNRESOLVED, GRAPH_RESOLVED, ARTIFACTS_RESOLVED}

    ResolutionStrategyInternal getResolutionStrategy();

    String getPath();

    void triggerWhenEmptyActionsIfNecessary();

    void markAsObserved(InternalState requestedState);

    void addMutationValidator(MutationValidator validator);

    void removeMutationValidator(MutationValidator validator);


    /**
     * Locks a configuration, making it effectively immutable. Any attempt to mutate this configuration
     * will throw an exception with the provided error message.
     * @param message the message to be sent to the user when an attempt to mutate the configuration is done.
     */
    void lock(String message);

}
