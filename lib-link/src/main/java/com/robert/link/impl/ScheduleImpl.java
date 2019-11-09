package com.robert.link.impl;

import com.robert.link.core.Scheduler;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduleImpl implements Scheduler {

    private final ScheduledExecutorService service;

    public ScheduleImpl(int poolSize) {
        IoSelectorProvider.IoProviderThreadFactory factory
                = new IoSelectorProvider.IoProviderThreadFactory("scheduled-thread-pool");
        this.service = Executors.newScheduledThreadPool(poolSize, factory);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        return service.schedule(runnable, delay, timeUnit);
    }

    @Override
    public void close() throws IOException {
        service.shutdownNow();
    }
}
