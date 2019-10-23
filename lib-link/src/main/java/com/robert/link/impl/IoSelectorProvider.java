package com.robert.link.impl;

import com.robert.link.core.IoProvider;
import com.robert.util.CloseUtils;
import com.sun.istack.internal.Nullable;
import com.sun.org.apache.bcel.internal.generic.Select;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
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

        //开启轮询读写就绪的channel
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
                            waitSelection(inRegInput);
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                //异步处理读取数据，不阻塞当前轮询器
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
                    try {
                        if (writeSelector.select() == 0) {
                            waitSelection(inRegOutput);
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                //异步处理写入数据，不阻塞当前轮询器
                                handleSelection(selectionKey, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlePool);
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


    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap);
    }

    @Override
    public void close() {
        if (isClose.compareAndSet(false, true)) {
            //关闭线程池
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();
            //清空回调
            inputCallbackMap.clear();
            outputCallbackMap.clear();
            //唤醒selector
            writeSelector.wakeup();
            readSelector.wakeup();
            //关闭selector
            CloseUtils.close(writeSelector, readSelector);
        }
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


    /**
     * 根据locker判断当前是否处于注册阶段，
     * 由于注册阶段会有一次唤醒操作，不会阻塞在select()状态
     *
     * @param locker 注册读或者注册写的
     */
    private static void waitSelection(final AtomicBoolean locker) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            if (locker.get()) {
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private static SelectionKey registerSelection(SocketChannel channel, Selector selector,
                                                  int registerOps, AtomicBoolean locker,
                                                  Map<SelectionKey, Runnable> outputCallbackMap,
                                                  Runnable callback) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            //设置进入注册阶段
            locker.set(true);
            try {
                selector.wakeup();
                SelectionKey key = null;
                if (channel.isRegistered()) {
                    //查询是否已经注册过
                    key = channel.keyFor(selector);
                    if (key != null) {
                        key.interestOps(key.readyOps() | registerOps);
                    }
                }

                if (key == null) {
                    try {
                        //注册selector得到key
                        key = channel.register(selector, registerOps);
                        //根据key
                        outputCallbackMap.put(key, callback);
                    } catch (ClosedChannelException ignore) {
                        return null;
                    }
                }
                return key;
            } finally {
                locker.set(false);
                try {
                    locker.notify();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private static void unRegisterSelection(SocketChannel channel, Selector selector,
                                            Map<SelectionKey, Runnable> callbackMap) {
        if (channel.isRegistered()) {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                //取消监听时间
                key.cancel();
                //移除回调
                callbackMap.remove(key);
                //唤醒退出循环
                selector.wakeup();
            }
        }
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
