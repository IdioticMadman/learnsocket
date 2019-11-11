package com.robert.link.core;

import java.io.Closeable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Scheduler extends Closeable {

    ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit);

    void delivery(Runnable runnable);
}
