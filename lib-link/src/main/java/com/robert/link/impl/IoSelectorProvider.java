package com.robert.link.impl;

import com.robert.link.core.IoProvider;
import com.sun.istack.internal.Nullable;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoSelectorProvider implements IoProvider {

    private final AtomicBoolean isClose = new AtomicBoolean(false);
    // 是否处于某个过程
    private final AtomicBoolean inRegInput = new AtomicBoolean(false);
    private final AtomicBoolean inRegOutput = new AtomicBoolean(false);

    private final Map<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final Map<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService outputHandlePool;
    private final ExecutorService inputHandlePool;
    private final Selector writeSelector;
    private final Selector readSelector;

    public IoSelectorProvider() throws IOException {
        this.writeSelector = Selector.open();
        this.readSelector = Selector.open();

        this.inputHandlePool = Executors.newFixedThreadPool(5,
                new IoProviderThreadFactory("IoProvider-input-thread"));

        this.outputHandlePool = Executors.newFixedThreadPool(5,
                new IoProviderThreadFactory("IoProvider-output-thread"));

        startRead();
        startWrite();
    }

    private void startRead() {
        Thread thread = new Thread("IoSelectorProvider ReadSelector Thread") {
            @Override
            public void run() {
                while (!isClose.get()) {
                    try {
                        if (readSelector.select() == 0) {
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                handleSelection(selectionKey, SelectionKey.OP_READ, inputCallbackMap, inputHandlePool);
                            }
                        }
                        selectionKeys.clear();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }


    private void startWrite() {
        Thread thread = new Thread("IoSelectorProvider WriteSelector Thread") {
            @Override
            public void run() {
                while (!isClose.get()) {

                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandlerInputCallback inputCallback) {

        return registerSelection(channel, readSelector, SelectionKey.OP_READ,
                inRegInput, inputCallbackMap, inputCallback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandlerOutputCallback outputCallback) {

        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE,
                inRegOutput, outputCallbackMap, outputCallback) != null;
    }


    private static void handleSelection(SelectionKey key, int keyOps,
                                        Map<SelectionKey, Runnable> callbackMap,
                                        ExecutorService handlePool) {
        //取消监听
        key.interestOps(key.readyOps() & ~keyOps);
        Runnable runnable = callbackMap.get(key);
        if (runnable != null && !handlePool.isShutdown()) {
            //异步执行
            handlePool.execute(runnable);
        }
    }

    private static SelectionKey registerSelection(SocketChannel channel, Selector selector,
                                                  int keyOps, AtomicBoolean locker,
                                                  Map<SelectionKey, Runnable> outputCallbackMap,
                                                  Runnable callback) {
        synchronized (locker) {
            locker.set(true);
            try {
                selector.wakeup();


            } finally {
                locker.set(false);
                try {
                    locker.notify();
                } catch (Exception ignore) {
                }
            }
        }
        return null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {

    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {

    }

    @Override
    public void close() throws IOException {

    }

    /**
     * The default thread factory
     */
    static class IoProviderThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoProviderThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
