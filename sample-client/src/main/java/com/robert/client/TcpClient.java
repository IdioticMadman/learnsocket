package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TcpClient {


    private final ReadHandler readHandler;
    private final Socket socket;
    private final PrintStream printStream;

    public TcpClient(Socket socket, ReadHandler readHandler) throws IOException {
        this.socket = socket;
        this.readHandler = readHandler;
        this.printStream = new PrintStream(socket.getOutputStream());

    }

    public void exit() {
        readHandler.exit();
        CloseUtils.close(printStream);
        CloseUtils.close(socket);
    }

    public void send(String msg) {
        printStream.println(msg);
    }

    public static TcpClient startConnect(ServerInfo serverInfo) {
        try {
            Socket socket;
            ReadHandler readHandler;
            socket = new Socket();
            socket.connect(new InetSocketAddress(serverInfo.getAddress(), serverInfo.getPort()));
            PrintUtil.println("已发起服务器连接，并进入后续程序");
            PrintUtil.println("客户端信息： " + socket.getLocalAddress() + ", p: " + socket.getLocalPort());
            PrintUtil.println("服务端信息：" + socket.getInetAddress() + "， P:" + socket.getPort());
            readHandler = new ReadHandler(socket.getInputStream());
            readHandler.start();
            return new TcpClient(socket, readHandler);
        } catch (IOException e) {
            e.printStackTrace();
            PrintUtil.println("客户端异常退出。。。");
        }
        return null;
    }


    private final static class ReadHandler extends Thread {

        private final InputStream inputStream;
        private boolean done = false;

        public ReadHandler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                do {
                    String msg = null;
                    try {
                        msg = reader.readLine();
                    } catch (SocketTimeoutException e) {
                        e.printStackTrace();
                    }
                    if (msg == null) {
                        System.out.println("连接已关闭，无法读取数据~");
                        break;
                    }
                    PrintUtil.println("客户端接收到消息：%s", msg);
                } while (!done);
            } catch (Exception e) {
                if (!done) {
                    PrintUtil.println("连接异常断开" + e.getMessage());
                }
            } finally {
                CloseUtils.close(inputStream);
            }
        }

        public void exit() {
            done = true;
            CloseUtils.close(inputStream);
        }
    }

}
