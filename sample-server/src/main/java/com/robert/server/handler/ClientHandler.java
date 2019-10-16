package com.robert.server.handler;

import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {

    private final ReadHandler readHandler;
    private final WriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    private final Socket socket;
    private final String clientInfo;

    /**
     * @param socket                客户端连接的socket
     * @param clientHandlerCallback 当前客户端的回调
     * @throws IOException 操作异常
     */
    public ClientHandler(Socket socket, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socket = socket;
        this.readHandler = new ReadHandler(socket.getInputStream());
        this.writeHandler = new WriteHandler(socket.getOutputStream());
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = "A [" + socket.getInetAddress().getHostName() + "], P[" + socket.getPort() + "]";
        PrintUtil.println("新客户端连接：" + socket.getInetAddress() + " P:" + socket.getPort());
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
        PrintUtil.println("客户端已退出：" + socket.getInetAddress() + " P:" + socket.getPort());
    }

    private class ReadHandler extends Thread {

        private final InputStream inputStream;
        private boolean done = false;

        ReadHandler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                BufferedReader is = new BufferedReader(new InputStreamReader(inputStream));
                do {
                    //读取客户端发送的消息
                    String message = is.readLine();
                    if (message == null) {
                        PrintUtil.println("客户端已无法获取数据");
                        ClientHandler.this.exitBySelf();
                        break;
                    }
                    clientHandlerCallback.onMessageArrived(ClientHandler.this, message);
                } while (!done);
            } catch (IOException e) {
                if (!done) {
                    PrintUtil.println("链接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                CloseUtils.close(inputStream);
            }
        }

        void exit() {
            done = true;
            CloseUtils.close(inputStream);
        }
    }


    private class WriteHandler {
        private final PrintStream printStream;
        private final ExecutorService executorService;
        private boolean done = false;

        WriteHandler(OutputStream outputStream) {
            printStream = new PrintStream(outputStream);
            executorService = Executors.newSingleThreadExecutor();
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
            CloseUtils.close(printStream);
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
                try {
                    WriteHandler.this.printStream.println(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
