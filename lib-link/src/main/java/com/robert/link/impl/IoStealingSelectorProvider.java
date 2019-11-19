package com.robert.link.impl;

import com.robert.link.core.IoProvider;
import com.robert.link.impl.stealing.IoTask;
import com.robert.link.impl.stealing.StealingSelectorThread;
import com.robert.link.impl.stealing.StealingService;

import java.io.IOException;
import java.nio.channels.SelectionKey;
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
            thread.start();
        }
        this.stealingThreads = threads;
        this.stealingService = stealingService;
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleProviderCallback inputCallback) {
        StealingSelectorThread thread = this.stealingService.getNotBusyThread();
        if (thread != null) {
            return thread.register(channel, SelectionKey.OP_READ, inputCallback);
        }
        return false;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleProviderCallback outputCallback) {
        StealingSelectorThread thread = this.stealingService.getNotBusyThread();
        if (thread != null) {
            return thread.register(channel, SelectionKey.OP_WRITE, outputCallback);
        }
        return false;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        for (StealingSelectorThread stealingThread : this.stealingThreads) {
            stealingThread.unRegister(channel);
        }
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {

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
            task.providerCallback.run();
            return false;
        }
    }
}
