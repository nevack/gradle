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

package org.gradle.internal.enterprise.impl;

import org.gradle.internal.enterprise.GradleEnterprisePluginBackgroundJobExecutor;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@ServiceScope(Scopes.Gradle.class)
public class DefaultGradleEnterprisePluginBackgroundJobExecutor implements GradleEnterprisePluginBackgroundJobExecutor {
    private final ThreadPoolExecutor executorService = createExecutor();
    private final List<Task> tasks = new ArrayList<>();

    private static ThreadPoolExecutor createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4, 4,
            30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new BackgroundThreadFactory()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Override
    public boolean execute(Callable<?> backgroundAction, Consumer<? super Throwable> onError) {
        try {
            Future<?> actionFuture = executorService.submit(backgroundAction);
            synchronized (tasks) {
                tasks.add(new Task(actionFuture, onError));
            }
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    @Override
    public boolean isInBackground() {
        return Thread.currentThread() instanceof BackgroundThread;
    }

    @Override
    public void shutdown() {
        if (executorService.isShutdown()) {
            return;
        }
        executorService.shutdown();

        List<Task> submittedTasks;
        synchronized (tasks) {
            submittedTasks = new ArrayList<>(tasks);
            tasks.clear();
        }
        for (Task task : submittedTasks) {
            try {
                task.result.get();
            } catch (InterruptedException e) {
                //noinspection ResultOfMethodCallIgnored
                Thread.interrupted(); // clear interrupt
                return;
            } catch (ExecutionException e) {
                // Move error handling back to the Gradle managed thread so that:
                // 1. The logging is consistently at the end of the build
                // 2. There is a bound build operation, allowing us to capture/assign the output
                task.onError.accept(e.getCause());
            }
        }
    }

    private static final class BackgroundThreadFactory implements ThreadFactory {
        private static final String NAME = "gradle-enterprise-background";

        private final ThreadGroup group = new ThreadGroup(NAME);
        private final AtomicLong counter = new AtomicLong();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new BackgroundThread(group, r, NAME + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class BackgroundThread extends Thread {
        BackgroundThread(ThreadGroup group, Runnable r, String s) {
            super(group, r, s);
        }
    }

    private static class Task {
        final Future<?> result;
        final Consumer<? super Throwable> onError;

        public Task(Future<?> result, Consumer<? super Throwable> onError) {
            this.result = result;
            this.onError = onError;
        }
    }
}
