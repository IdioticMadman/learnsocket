package com.robert.link.impl.stealing;

import com.robert.link.core.IoProvider;
import com.robert.link.core.IoTask;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("MagicConstant")
public abstract class StealingSelectorThread extends Thread {

    private static final int MAX_ONCE_READ_TASK = 128;
    private static final int MAX_ONCE_WRITE_TASK = 128;
    private static final int MAX_ONCE_RUN_TASK = MAX_ONCE_READ_TASK + MAX_ONCE_WRITE_TASK;

    //允许的操作
    private static final int VALID_OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;

    private final Selector selector;

    private volatile boolean isRunning = true;
    //准备就绪队列，可以进行IO操作了
    private final ArrayBlockingQueue<IoTask> readyTaskQueue = new ArrayBlockingQueue<>(MAX_ONCE_RUN_TASK);
    //需注册到channel上面的task的队列
    private final ConcurrentLinkedQueue<IoTask> registerTaskQueue = new ConcurrentLinkedQueue<>();
    //饱和度
    private final AtomicLong saturatingCapacity = new AtomicLong();
    //用于多线程协同的工具
    private volatile StealingService stealingService;
    //解除注册的锁
    private final AtomicBoolean unRegisterLocker = new AtomicBoolean(false);

    public StealingSelectorThread(Selector selector) {
        this.selector = selector;
    }

    Queue<IoTask> getReadyTaskQueue() {
        return readyTaskQueue;
    }

    long getSaturatingCapacity() {
        if (selector.isOpen()) {
            return saturatingCapacity.get();
        }
        return -1;
    }

    /**
     * 将单次就绪的队列加入总队列中
     */
    private void joinTaskQueue(ArrayBlockingQueue<IoTask> readyTaskQueue, List<IoTask> onceReadyTaskCache) {
        readyTaskQueue.addAll(onceReadyTaskCache);
    }

    /**
     * 将通道注册到当前selector
     */
    public void register(IoTask task) {
        if ((task.ops & ~VALID_OPS) != 0) {
            throw new UnsupportedOperationException("Unsupported register ops: " + task.ops);
        }
        registerTaskQueue.offer(task);
        selector.wakeup();
    }

    /**
     * 取消注册
     */
    public void unRegister(SocketChannel channel) {
        SelectionKey selectionKey = channel.keyFor(selector);
        if (selectionKey != null && selectionKey.attachment() != null) {
            selectionKey.attach(null);
            if (Thread.currentThread() == this) {
                selectionKey.cancel();
            } else {
                synchronized (unRegisterLocker) {
                    //上锁取消
                    unRegisterLocker.set(true);
                    selector.wakeup();
                    selectionKey.cancel();
                    unRegisterLocker.set(false);
                }
            }
        }
    }

    @Override
    public void run() {
        final Selector selector = this.selector;
        final ConcurrentLinkedQueue<IoTask> registerTaskQueue = this.registerTaskQueue;
        final ArrayBlockingQueue<IoTask> readyTaskQueue = this.readyTaskQueue;
        final List<IoTask> onceReadyReadTaskCache = new ArrayList<>(MAX_ONCE_READ_TASK);
        final List<IoTask> onceReadyWriteTaskCache = new ArrayList<>(MAX_ONCE_WRITE_TASK);

        try {
            while (isRunning) {
                //将待注册的Queue中的task，都注册到selector上面
                consumeRegisterToDoTasks(selector, registerTaskQueue);

                int count = selector.select();

                while (unRegisterLocker.get()) {
                    Thread.yield();
                }

                if (count == 0) {
                    //检查一次
                    continue;
                }
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                int onceReadTaskCount = MAX_ONCE_READ_TASK;
                int onceWriteTaskCount = MAX_ONCE_WRITE_TASK;

                //拿到就绪的集合开始遍历
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    Object attachment = key.attachment();
                    if (key.isValid() && attachment instanceof KeyAttachment) {
                        final KeyAttachment keyAttachment = (KeyAttachment) attachment;
                        try {
                            int readyOps = key.readyOps();
                            int interestOps = key.interestOps();

                            if ((readyOps & SelectionKey.OP_READ) != 0 && --onceReadTaskCount > 0) {
                                //是否可读
                                onceReadyReadTaskCache.add(keyAttachment.taskForReadable);
                                interestOps = interestOps & ~SelectionKey.OP_READ;
                            }

                            if ((readyOps & SelectionKey.OP_WRITE) != 0 && --onceWriteTaskCount > 0) {
                                //是否可写
                                onceReadyWriteTaskCache.add(keyAttachment.taskForWritable);
                                interestOps = interestOps & ~SelectionKey.OP_WRITE;
                            }
                            //取消已就绪的Ops
                            key.interestOps(interestOps);
                        } catch (CancelledKeyException e) {
                            //当前连接被取消，直接移出相关任务
                            if (keyAttachment.taskForReadable != null) {
                                onceReadyReadTaskCache.remove(keyAttachment.taskForReadable);
                            }
                            if (keyAttachment.taskForWritable != null) {
                                onceReadyWriteTaskCache.remove(keyAttachment.taskForWritable);
                            }
                        }
                    }
                    iterator.remove();
                }
                if (!onceReadyReadTaskCache.isEmpty()) {
                    //添加到就绪队列
                    joinTaskQueue(readyTaskQueue, onceReadyReadTaskCache);
                    onceReadyReadTaskCache.clear();
                }

                if (!onceReadyWriteTaskCache.isEmpty()) {
                    //添加到就绪队列
                    joinTaskQueue(readyTaskQueue, onceReadyWriteTaskCache);
                    onceReadyWriteTaskCache.clear();
                }
                //执行ready的task
                consumeTodoTasks(readyTaskQueue, registerTaskQueue);
            }

        } catch (ClosedSelectorException ignore) {
        } catch (IOException e) {
            CloseUtils.close(selector);
        } finally {
            readyTaskQueue.clear();
            registerTaskQueue.clear();
        }
    }

    /**
     * 消费待完成的任务
     */
    private void consumeTodoTasks(ArrayBlockingQueue<IoTask> readyTaskQueue, ConcurrentLinkedQueue<IoTask> registerTaskQueue) {
        final AtomicLong saturatingCapacity = this.saturatingCapacity;

        IoTask task = readyTaskQueue.poll();
        while (task != null) {
            saturatingCapacity.incrementAndGet();
            if (processTask(task)) {
                registerTaskQueue.offer(task);
            }
            task = readyTaskQueue.poll();
        }
        //窃取其他的任务
        final StealingService stealingService = this.stealingService;
        if (stealingService != null) {
            task = stealingService.steal(readyTaskQueue);
            while (task != null) {
                saturatingCapacity.incrementAndGet();
                if (processTask(task)) {
                    registerTaskQueue.offer(task);
                }
                task = stealingService.steal(readyTaskQueue);
            }
        }

    }


    //消费掉待注册的操作
    private void consumeRegisterToDoTasks(Selector selector, ConcurrentLinkedQueue<IoTask> registerTaskQueue) {
        IoTask registerTask = registerTaskQueue.poll();
        while (registerTask != null) {
            try {
                final SocketChannel channel = registerTask.channel;
                int ops = registerTask.ops;

                //注册
                SelectionKey selectionKey = channel.keyFor(selector);
                if (selectionKey == null) {
                    selectionKey = channel.register(selector, ops, new KeyAttachment());
                } else {
                    selectionKey.interestOps(selectionKey.interestOps() | ops);
                }
                Object attachment = selectionKey.attachment();
                if (attachment instanceof KeyAttachment) {
                    ((KeyAttachment) attachment).attach(ops, registerTask);
                } else {
                    selectionKey.cancel();
                }
            } catch (ClosedChannelException |
                    CancelledKeyException |
                    ClosedSelectorException e) {
                registerTask.fireThrowable(e);
            } finally {
                registerTask = registerTaskQueue.poll();
            }
        }
    }

    public void exit() {
        isRunning = false;
        CloseUtils.close(selector);
        interrupt();
    }


    /**
     * 调用子类执行任务操作
     */
    protected abstract boolean processTask(IoTask task);

    public void setStealingService(StealingService stealingService) {
        this.stealingService = stealingService;
    }

    /**
     * 用以注册时添加的组件
     */
    static class KeyAttachment {
        IoTask taskForReadable;
        IoTask taskForWritable;

        void attach(int ops, IoTask task) {
            if (ops == SelectionKey.OP_WRITE) {
                taskForWritable = task;
            } else {
                taskForReadable = task;
            }
        }

    }
}
