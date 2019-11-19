package com.robert.link.impl;

import com.robert.link.core.IoProvider;
import com.robert.link.core.IoTask;
import com.robert.link.impl.stealing.StealingSelectorThread;
import com.robert.link.impl.stealing.StealingService;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class IoStealingSelectorProvider implements IoProvider {

    private final StealingSelectorThread[] stealingThreads;
    private final StealingService stealingService;

    public IoStealingSelectorProvider(int poolSize) throws IOException {
        StealingSelectorThread[] threads = new IoStealingThread[poolSize];
        for (int i = 0; i < poolSize; i++) {
            Selector selector = Selector.open();
            String name = "IoProvider-Thread-" + (i + 1);
            threads[i] = new IoStealingThread(name, selector);
        }
        StealingService stealingService = new StealingService(threads, 10);
        for (StealingSelectorThread thread : threads) {
            thread.setStealingService(stealingService);
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        }
        this.stealingThreads = threads;
        this.stealingService = stealingService;
    }

    @Override
    public void register(HandleProviderCallback callback) throws IOException {
        StealingSelectorThread thread = this.stealingService.getNotBusyThread();
        if (thread == null) {
            throw new IOException("IoStealingSelectorProvider is shutdown!!");
        }
        thread.register(callback);
    }

    @Override
    public void unRegister(SocketChannel channel) {
        if (!channel.isConnected()) {
            return;
        }
        for (StealingSelectorThread stealingThread : stealingThreads) {
            stealingThread.unRegister(channel);
        }
    }

    @Override
    public void close() throws IOException {
        stealingService.shutdown();
    }


    static class IoStealingThread extends StealingSelectorThread {

        public IoStealingThread(String name, Selector selector) {
            super(selector);
            setName(name);
        }

        @Override
        protected boolean processTask(IoTask task) {
            return task.onProcessIo();
        }
    }
}
