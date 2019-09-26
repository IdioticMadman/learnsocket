package com.robert.sample.client;

import com.robert.sample.client.bean.ServerInfo;
import com.robert.util.CloseUtils;
import com.robert.util.PrintUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class TcpClient {


    public static void startConnect(ServerInfo serverInfo) {
        ReadHandler readHandler = null;
        Socket socket = null;
        try {
            socket = new Socket();
//            socket.setSoTimeout(3000);
            socket.connect(new InetSocketAddress(serverInfo.getAddress(), serverInfo.getPort()));
            PrintUtil.println("已发起服务器连接，并进入后续程序");
            PrintUtil.println("客户端信息： " + socket.getLocalAddress() + ", p: " + socket.getLocalPort());
            PrintUtil.println("服务端信息：" + socket.getInetAddress() + "， P:" + socket.getPort());
            readHandler = new ReadHandler(socket.getInputStream());
            readHandler.start();
            write(socket);
        } catch (IOException e) {
            e.printStackTrace();
            PrintUtil.println("客户端异常退出。。。");
        } finally {
            if (readHandler != null) {
                readHandler.exit();
            }
            CloseUtils.close(socket);
            PrintUtil.println("客户端退出");
        }
    }

    private static void write(Socket socket) throws IOException {

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String result = "";
        PrintStream printStream = new PrintStream(socket.getOutputStream());
        do {
            result = reader.readLine();
            printStream.println(result);
        } while (!"00bye00".equalsIgnoreCase(result));
        CloseUtils.close(printStream);
        CloseUtils.close(reader);
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
