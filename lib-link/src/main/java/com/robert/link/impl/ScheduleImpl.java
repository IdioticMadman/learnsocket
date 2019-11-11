package com.robert.link.impl;

import com.robert.NameableThreadFactory;
import com.robert.link.core.Scheduler;

import java.io.IOException;
import java.util.concurrent.*;

public class ScheduleImpl implements Scheduler {

    private final ScheduledExecutorService scheduledService;
    private final ExecutorService deliveryService;

    public ScheduleImpl(int poolSize) {
        this.scheduledService = Executors.newScheduledThreadPool(poolSize,
                new NameableThreadFactory("scheduled-thread-pool"));
        this.deliveryService = Executors.newFixedThreadPool(1,
                new NameableThreadFactory("delivery-thread-pool"));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        return scheduledService.schedule(runnable, delay, timeUnit);
    }

    @Override
    public void delivery(Runnable runnable) {
        deliveryService.execute(runnable);
    }

    @Override
    public void close() throws IOException {
        scheduledService.shutdownNow();
        deliveryService.shutdownNow();
    }
}
