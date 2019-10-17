package com.robert.server.handler;

import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {

    private final ReadHandler readHandler;
    private final WriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    private final SocketChannel socket;
    private final String clientInfo;

    /**
     * @param socket                客户端连接的socket
     * @param clientHandlerCallback 当前客户端的回调
     * @throws IOException 操作异常
     */
    public ClientHandler(SocketChannel socket, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socket = socket;
        socket.configureBlocking(false);
        Selector readSelector = Selector.open();
        socket.register(readSelector, SelectionKey.OP_READ);
        this.readHandler = new ReadHandler(readSelector);

        Selector writeSelector = Selector.open();
        socket.register(writeSelector, SelectionKey.OP_WRITE);
        this.writeHandler = new WriteHandler(writeSelector);

        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socket.getRemoteAddress().toString();
        PrintUtil.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    /**
     * 开启接收客户端消息
     */
    public void readToPrint() {
        readHandler.start();
    }

    /**
     * 发送消息
     *
     * @param message
     */
    public void send(String message) {
        writeHandler.send(message);
    }

    /**
     * 异常退出
     */
    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    public interface ClientHandlerCallback {
        void onSelfClosed(ClientHandler handler);

        void onMessageArrived(ClientHandler handler, String msg);
    }

    /**
     * 退出，释放资源
     */
    public void exit() {
        readHandler.exit();
        writeHandler.exit();
        CloseUtils.close(socket);
        PrintUtil.println("客户端已退出：" + clientInfo);
    }

    private class ReadHandler extends Thread {

        private final Selector selector;
        private final ByteBuffer byteBuffer;
        private boolean done = false;


        ReadHandler(Selector selector) {
            this.selector = selector;
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {
            try {
                ByteBuffer byteBuffer = this.byteBuffer;
                do {
                    if (selector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done) {
                            break;
                        }
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (key.isReadable()) {
                            SocketChannel channel = (SocketChannel) key.channel();
                            //清空byteBuffer
                            byteBuffer.clear();
                            //读取数据
                            int read = channel.read(byteBuffer);
                            if (read > 0) {
                                //丢弃掉换行符
                                String msg = new String(byteBuffer.array(), 0, read - 1);
                                clientHandlerCallback.onMessageArrived(ClientHandler.this, msg);
                            } else {
                                PrintUtil.println("客户端已无法获取数据");
                                ClientHandler.this.exitBySelf();
                                break;
                            }
                        }
                    }
                } while (!done);
            } catch (IOException e) {
                if (!done) {
                    PrintUtil.println("链接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                CloseUtils.close(selector);
            }
        }

        void exit() {
            done = true;
            selector.wakeup();
            CloseUtils.close(selector);
        }
    }


    private class WriteHandler {
        private final Selector selector;
        private final ExecutorService executorService;
        private final ByteBuffer byteBuffer;
        private boolean done = false;

        WriteHandler(Selector selector) {
            this.selector = selector;
            executorService = Executors.newSingleThreadExecutor();
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        /**
         * 发送消息
         *
         * @param message
         */
        void send(String message) {
            if (!done) {
                executorService.execute(new WriteRunnable(message));
            }
        }

        /**
         * 退出
         */
        void exit() {
            this.done = true;
            CloseUtils.close(selector);
            executorService.shutdown();
        }

        /**
         * 写出消息
         */
        class WriteRunnable implements Runnable {

            private final String message;

            WriteRunnable(String message) {
                this.message = message;
            }

            @Override
            public void run() {
                if (WriteHandler.this.done) {
                    return;
                }
                byteBuffer.clear();
                byteBuffer.put((message + "\n").getBytes(StandardCharsets.UTF_8));
                byteBuffer.flip();

                try {
                    while (!done && byteBuffer.hasRemaining()) {
                        int len = socket.write(byteBuffer);
                        if (len < 0) {
                            PrintUtil.println("客户端已无法发送数据！");
                            exitBySelf();
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
