package com.robert.sample.client;

import com.robert.sample.client.bean.ServerInfo;
import com.robert.sample.common.UDPConstants;
import com.robert.util.ByteUtil;
import com.robert.util.PrintUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UDPSearcher {

    public static ServerInfo searchServer(int timeout) {

        //1. 发送广播
        //2. 等待接受广播
        //3. 解析回应
        //4. 返回服务信息
        Listener listener = null;
        try {
            CountDownLatch receiverLatch = new CountDownLatch(1);
            //启动监听
            listener = startListener(receiverLatch);
            //发送广播
            sendBroadCast();
            //等待收到广播
            receiverLatch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        if (listener == null) {
            return null;
        }
        return listener.getServerInfo();
    }

    //开启监听
    private static Listener startListener(CountDownLatch recevierLatch) throws InterruptedException {
        PrintUtil.println("UDPSearcher start listen...");
        CountDownLatch startLatch = new CountDownLatch(1);
        Listener listener = new Listener(UDPConstants.PORT_CLIENT_RESPONSE, startLatch, recevierLatch);
        listener.start();
        startLatch.await();
        return listener;
    }


    private static void sendBroadCast() throws IOException {
        DatagramSocket ds = new DatagramSocket();
        byte[] buffer = new byte[128];
        ByteBuffer wrap = ByteBuffer.wrap(buffer);
        //公共头
        wrap.put(UDPConstants.HEADER);
        wrap.putShort(UDPConstants.CMD_SEARCH_SERVER_REQ);
        wrap.putInt(UDPConstants.PORT_CLIENT_RESPONSE);
        DatagramPacket packet = new DatagramPacket(buffer, wrap.position());
        packet.setAddress(InetAddress.getByName("255.255.255.255"));
        packet.setPort(UDPConstants.PORT_SERVER);
        ds.send(packet);
        ds.close();
        PrintUtil.println("UDPSearcher Broadcast finished。");
    }


    private static final class Listener extends Thread {

        //收到回调
        private final CountDownLatch receiverLatch;
        //开始运行
        private final CountDownLatch startLatch;
        //监听的端口
        private final int portClientResponse;

        private DatagramSocket ds;

        private byte[] buffer = new byte[128];

        private boolean done = false;

        private List<ServerInfo> serverInfos = new ArrayList<>();

        private int minLength = UDPConstants.HEADER.length + 2 + 4;


        public Listener(int portClientResponse, CountDownLatch startLatch, CountDownLatch receiverLatch) {
            this.receiverLatch = receiverLatch;
            this.startLatch = startLatch;
            this.portClientResponse = portClientResponse;
        }

        @Override
        public void run() {
            startLatch.countDown();
            try {
                ds = new DatagramSocket(portClientResponse);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                while (!done) {
                    //1. 接收回应包
                    ds.receive(packet);

                    byte[] receiveData = packet.getData();
                    //2. 获取客户端信息
                    boolean isValid = packet.getLength() >= minLength &&
                            ByteUtil.startWith(receiveData, UDPConstants.HEADER);

                    String address = packet.getAddress().getHostName();
                    int port = packet.getPort();
                    PrintUtil.println("UDPSearcher receive message from ip:%s , port: %d", address, port);
                    //3. 判断是否合法
                    if (!isValid) {
                        continue;
                    }

                    //4. 读取数据
                    ByteBuffer buffer = ByteBuffer.wrap(receiveData, UDPConstants.HEADER.length, packet.getLength());
                    short cmd = buffer.getShort();
                    int serverPort = buffer.getInt();
                    //5. 判断数据是否正确
                    if (cmd != UDPConstants.CMD_SEARCH_SERVER_RESP || serverPort <= 0) {
                        PrintUtil.println("UDPSearcher receive cmd:" + cmd + "\tserverPort:" + serverPort);
                        continue;
                    }
                    String sn = new String(receiveData, minLength, packet.getLength() - minLength);
                    //6. 构建响应
                    ServerInfo serverInfo = new ServerInfo(sn, packet.getAddress(), serverPort);
                    serverInfos.add(serverInfo);
                    receiverLatch.countDown();
                }

            } catch (IOException ignore) {
            } finally {
                close();
            }

        }

        public void close() {
            if (ds != null) {
                ds.close();
                ds = null;
            }
        }

        public ServerInfo getServerInfo() {
            if (serverInfos.size() > 0) {
                close();
                return serverInfos.get(0);
            }
            return null;
        }
    }
}
