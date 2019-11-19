package com.robert.link.impl.stealing;

import com.robert.link.core.IoTask;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntFunction;

public class StealingService {

    private final int minSafetyThreshold;

    private final StealingSelectorThread[] threads;

    private final Queue<IoTask>[] queues;

    private volatile boolean isTerminated = false;

    public StealingService(StealingSelectorThread[] stealingThreads, int minSafetyThreshold) {
        this.threads = stealingThreads;
        this.minSafetyThreshold = minSafetyThreshold;
        this.queues = Arrays.stream(threads)
                .map(StealingSelectorThread::getReadyTaskQueue)
                .toArray((IntFunction<Queue<IoTask>[]>) Queue[]::new);
    }

    /**
     * 排除自己的队列，从别人的队列中窃取一个任务出来
     */
    IoTask steal(ArrayBlockingQueue<IoTask> excludedQueue) {
        final int minSafetyThreshold = this.minSafetyThreshold;
        final Queue<IoTask>[] queues = this.queues;
        for (Queue<IoTask> queue : queues) {
            if (queue == excludedQueue) {
                continue;
            }
            int size = queue.size();
            if (size > minSafetyThreshold) {
                IoTask poll = queue.poll();
                if (poll != null) {
                    return poll;
                }
            }
        }
        return null;
    }

    public void shutdown() {
        if (isTerminated) {
            return;
        }
        isTerminated = true;
        for (StealingSelectorThread thread : threads) {
            thread.exit();
        }
    }

    public StealingSelectorThread getNotBusyThread() {
        StealingSelectorThread targetThread = null;
        long targetKeyCount = Long.MAX_VALUE;
        for (StealingSelectorThread thread : threads) {
            long saturatingCapacity = thread.getSaturatingCapacity();
            if (saturatingCapacity != -1 && saturatingCapacity < targetKeyCount) {
                targetKeyCount = saturatingCapacity;
                targetThread = thread;
            }
        }
        return targetThread;
    }


    public boolean isTerminated() {
        return isTerminated;
    }


}
