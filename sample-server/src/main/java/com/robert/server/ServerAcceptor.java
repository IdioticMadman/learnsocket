package com.robert.server;

import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class ServerAcceptor extends Thread {

    private boolean done = false;
    private final Selector selector;
    private final AcceptListener listener;

    private final CountDownLatch countDown = new CountDownLatch(1);

    public ServerAcceptor(AcceptListener listener) throws IOException {
        super("server-accept-selector");
        this.selector = Selector.open();
        this.listener = listener;
    }

    boolean awaitRunning() {
        try {
            countDown.await();
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        countDown.countDown();
        Selector selector = this.selector;
        do {
            try {
                if (selector.select() == 0) {
                    //被唤醒，然后没有就绪队列，判断是否为主动退出
                    if (done) {
                        break;
                    }
                    continue;
                }
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    if (done) {
                        //判断是否结束
                        break;
                    }

                    SelectionKey key = iterator.next();
                    iterator.remove();
                    //客户端到达
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
                        SocketChannel socket = serverSocket.accept();
                        listener.onNewSocketArrived(socket);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                PrintUtil.println("客户端链接异常" + e.getMessage());
            }

        } while (!done);

        PrintUtil.println("ServerSelector finish");
    }

    public Selector getSelector() {
        return selector;
    }

    /**
     * 退出接受客户连接
     */
    public void exit() {
        done = true;
        CloseUtils.close(selector);
    }

    interface AcceptListener {
        void onNewSocketArrived(SocketChannel channel);
    }

}
