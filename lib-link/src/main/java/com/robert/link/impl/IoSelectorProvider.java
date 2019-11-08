package com.robert.link.impl;

import com.robert.link.core.IoProvider;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
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

        this.inputHandlePool = Executors.newFixedThreadPool(20,
                new IoProviderThreadFactory("IoProvider-input-thread"));

        this.outputHandlePool = Executors.newFixedThreadPool(20,
                new IoProviderThreadFactory("IoProvider-output-thread"));

        //开启轮询读写就绪的channel
        startRead();
        startWrite();
    }

    private void startRead() {
        String name = "IoSelectorProvider ReadSelector Thread";
        HandleThread thread = new HandleThread(name, isClose, readSelector, inRegInput,
                SelectionKey.OP_READ, inputCallbackMap, inputHandlePool);
        thread.start();
    }


    private void startWrite() {
        String name = "IoSelectorProvider WriteSelector Thread";
        HandleThread thread = new HandleThread(name, isClose, writeSelector, inRegOutput,
                SelectionKey.OP_WRITE, outputCallbackMap, outputHandlePool);
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleProviderCallback inputCallback) {

        return registerSelection(channel, readSelector, SelectionKey.OP_READ,
                inRegInput, inputCallbackMap, inputCallback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleProviderCallback outputCallback) {

        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE,
                inRegOutput, outputCallbackMap, outputCallback) != null;
    }


    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap, inRegInput);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap, inRegOutput);
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
            //直接关闭selector，不用wakeup
            CloseUtils.close(writeSelector, readSelector);
        }
    }


    private static void handleSelection(SelectionKey key, int keyOps,
                                        Map<SelectionKey, Runnable> callbackMap,
                                        ExecutorService handlePool, AtomicBoolean locker) {
        //取消监听
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            try {
                key.interestOps(key.readyOps() & ~keyOps);
            } catch (CancelledKeyException e) {
                return;
            }
        }
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
                                                  Map<SelectionKey, Runnable> callbackMap,
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
                    //注册selector得到key
                    key = channel.register(selector, registerOps);
                    //根据key
                    callbackMap.put(key, callback);
                }
                return key;
            } catch (ClosedChannelException | CancelledKeyException | ClosedSelectorException e) {
                return null;
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
                                            Map<SelectionKey, Runnable> callbackMap, AtomicBoolean locker) {

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            locker.set(true);
            selector.wakeup();
            try {
                if (channel.isRegistered()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        //取消监听时间
                        key.cancel();
                        //移除回调
                        callbackMap.remove(key);
                    }
                }
            } finally {
                locker.set(false);
                try {
                    locker.notifyAll();
                } catch (Exception ignore) {
                }
            }
        }
    }

    static class HandleThread extends Thread {

        private final AtomicBoolean isClose;
        private final Selector selector;
        private final AtomicBoolean locker;
        private final int ops;
        private final Map<SelectionKey, Runnable> callbackMap;
        private final ExecutorService handlerPool;

        HandleThread(String name, AtomicBoolean isClose, Selector selector,
                     AtomicBoolean locker, int ops, Map<SelectionKey, Runnable> callbackMap,
                     ExecutorService handlerPool) {
            super(name);
            setPriority(Thread.MAX_PRIORITY);
            this.isClose = isClose;
            this.selector = selector;
            this.locker = locker;
            this.ops = ops;
            this.callbackMap = callbackMap;
            this.handlerPool = handlerPool;
        }

        @Override
        public void run() {

            final AtomicBoolean isClose = this.isClose;
            final Selector selector = this.selector;
            final AtomicBoolean locker = this.locker;
            final int ops = this.ops;
            final Map<SelectionKey, Runnable> callbackMap = this.callbackMap;
            final ExecutorService handlerPool = this.handlerPool;

            while (!isClose.get()) {
                try {
                    if (selector.select() == 0) {
                        waitSelection(locker);
                        continue;
                    } else if (locker.get()) {
                        //注册时候被唤醒，但是返回了就绪的
                        waitSelection(locker);
                    }
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        if (selectionKey.isValid()) {
                            //异步处理读取数据，不阻塞当前轮询器
                            handleSelection(selectionKey, ops, callbackMap, handlerPool, locker);
                        }
                        iterator.remove();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClosedSelectorException e) {
                    break;
                }
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
