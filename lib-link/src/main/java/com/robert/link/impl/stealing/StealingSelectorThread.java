package com.robert.link.impl.stealing;

import com.robert.link.core.IoProvider;
import com.robert.link.core.IoTask;
import com.robert.util.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("MagicConstant")
public abstract class StealingSelectorThread extends Thread {

    //允许的操作
    private static final int VALID_OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;

    private final Selector selector;

    private volatile boolean isRunning = true;

    private final LinkedBlockingQueue<IoTask> readyTaskQueue = new LinkedBlockingQueue<>();

    private final LinkedBlockingQueue<IoTask> registerTaskQueue = new LinkedBlockingQueue<>();

    private final List<IoTask> onceReadyTaskCache = new ArrayList<>(200);

    private final AtomicLong saturatingCapacity = new AtomicLong();
    //用于多线程协同的工具
    private volatile StealingService stealingService;

    public StealingSelectorThread(Selector selector) {
        this.selector = selector;
    }

    public LinkedBlockingQueue<IoTask> getReadyTaskQueue() {
        return readyTaskQueue;
    }

    public long getSaturatingCapacity() {
        if (selector.isOpen()) {
            return saturatingCapacity.get();
        }
        return -1;
    }

    /**
     * 将单次就绪的队列加入总队列中
     */
    private void joinTaskQueue(LinkedBlockingQueue<IoTask> readyTaskQueue, List<IoTask> onceReadyTaskCache) {
        readyTaskQueue.addAll(onceReadyTaskCache);
    }

    /**
     * 将通道注册到当前selector
     */
    public boolean register(SocketChannel channel, int ops, IoProvider.HandleProviderCallback callback) {
        if (channel.isOpen()) {
            IoTask ioTask = new IoTask(channel, ops, callback);
            registerTaskQueue.offer(ioTask);
            return true;
        } else {
            return false;
        }
    }

    public void unRegister(SocketChannel channel) {
        SelectionKey selectionKey = channel.keyFor(selector);
        if (selectionKey != null && selectionKey.attachment() != null) {
            selectionKey.attach(null);
            //添加取消操作
            IoTask ioTask = new IoTask(channel, 0, null);
            registerTaskQueue.offer(ioTask);
        }
    }

    @Override
    public void run() {
        final Selector selector = this.selector;
        final LinkedBlockingQueue<IoTask> registerTaskQueue = this.registerTaskQueue;
        final LinkedBlockingQueue<IoTask> readyTaskQueue = this.readyTaskQueue;
        final List<IoTask> onceReadyTaskCache = this.onceReadyTaskCache;

        try {
            while (isRunning) {
                //将待注册的Queue中的task，都注册到selector上面
                consumeRegisterToDoTasks(selector, registerTaskQueue);

                if ((selector.selectNow()) == 0) {
                    //检查一次
                    Thread.yield();
                    continue;
                }
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                //拿到就绪的集合开始遍历
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    Object attachment = key.attachment();
                    if (key.isValid() && attachment instanceof KeyAttachment) {
                        final KeyAttachment keyAttachment = (KeyAttachment) attachment;
                        try {
                            int readyOps = key.readyOps();
                            int interestOps = key.interestOps();

                            if ((readyOps & SelectionKey.OP_READ) != 0) {
                                //是否可读
                                onceReadyTaskCache.add(keyAttachment.taskForReadable);
                                interestOps = interestOps & ~SelectionKey.OP_READ;
                            }

                            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                                //是否可写
                                onceReadyTaskCache.add(keyAttachment.taskForWritable);
                                interestOps = interestOps & ~SelectionKey.OP_WRITE;
                            }
                            //取消已就绪的Ops
                            key.interestOps(interestOps);
                        } catch (CancelledKeyException e) {
                            //当前连接被取消，直接移出相关任务
                            onceReadyTaskCache.remove(keyAttachment.taskForReadable);
                            onceReadyTaskCache.remove(keyAttachment.taskForWritable);
                        }
                    }
                    iterator.remove();
                }
                if (!onceReadyTaskCache.isEmpty()) {
                    //添加到就绪队列
                    joinTaskQueue(readyTaskQueue, onceReadyTaskCache);
                    onceReadyTaskCache.clear();
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
            onceReadyTaskCache.clear();
        }
    }

    /**
     * 消费待完成的任务
     */
    private void consumeTodoTasks(LinkedBlockingQueue<IoTask> readyTaskQueue, LinkedBlockingQueue<IoTask> registerTaskQueue) {
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
    private void consumeRegisterToDoTasks(Selector selector, LinkedBlockingQueue<IoTask> registerTaskQueue) {
        IoTask registerTask = registerTaskQueue.poll();
        while (registerTask != null) {
            try {
                final SocketChannel channel = registerTask.channel;
                int ops = registerTask.ops;
                if (ops == 0) {
                    //取消
                    SelectionKey selectionKey = channel.keyFor(selector);
                    if (selectionKey != null) {
                        selectionKey.cancel();
                    }
                } else if ((ops & ~VALID_OPS) == 0) {
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
                }
            } catch (ClosedChannelException |
                    CancelledKeyException |
                    ClosedSelectorException e) {
                e.printStackTrace();
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
