/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs submitted callables in the background. The implementation is provided by Gradle.
 */
public interface GradleEnterprisePluginBackgroundJobExecutor {
    /**
     * Submits the callable to be executed in the background worker.
     * The callable may be rejected if the executor is shut down with {@link #shutdown()}.
     *
     * @param backgroundAction the callable to execute
     * @param onError the callback to be executed at shutdown if the {@code backgroundAction} throws an exception
     * @return {@code true} if the callable was submitted successfully, {@code false} if the executor is shut down and the callable was rejected
     */
    boolean execute(Callable<?> backgroundAction, Consumer<? super Throwable> onError);

    /**
     * Returns {@code true} if the current thread is running the background task submitted to this executor.
     */
    boolean isInBackground();

    /**
     * Disallow submitting new tasks and blocks until all already submitted tasks complete.
     * The error-handling callbacks are invoked inside this method if any submitted callable failed.
     */
    void shutdown();
}
