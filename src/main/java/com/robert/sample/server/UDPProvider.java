package com.robert.sample.server;

import com.robert.sample.common.TCPConstants;
import com.robert.sample.common.UDPConstants;
import com.robert.util.ByteUtil;
import com.robert.util.PrintUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.UUID;

public class UDPProvider {

    private static Provider INSTANCE;

    public static void start() {
        stop();
        String sn = UUID.randomUUID().toString();
        Provider provider = new Provider(sn, TCPConstants.PORT_SERVER);
        provider.start();
        INSTANCE = provider;
    }

    public static void stop() {
        if (INSTANCE != null) {
            INSTANCE.exit();
            INSTANCE = null;
        }
    }

    private static final class Provider extends Thread {

        private final String tcpServerSn;
        private final int tcpServerPort;

        private DatagramSocket ds = null;
        private byte[] bytes = new byte[128];
        private boolean done = false;

        private Provider(String tcpServerSn, int tcpServerPort) {
            this.tcpServerSn = tcpServerSn;
            this.tcpServerPort = tcpServerPort;
        }

        @Override
        public void run() {
            PrintUtil.println("UDPProvider 启动");
            try {
                ds = new DatagramSocket(UDPConstants.PORT_SERVER);
                DatagramPacket receivePacket = new DatagramPacket(bytes, bytes.length);

                while (!done) {
                    //1. 接受客户端广播
                    ds.receive(receivePacket);
                    //2. 打印客户端，ip，端口
                    String clientAddress = receivePacket.getAddress().getHostAddress();
                    int clientPort = receivePacket.getPort();
                    int length = receivePacket.getLength();
                    byte[] receivePacketData = receivePacket.getData();
                    boolean isValid =
                            length >= (UDPConstants.HEADER.length + 2 + 4) &&
                                    ByteUtil.startWith(receivePacketData, UDPConstants.HEADER);

                    PrintUtil.println("UDPProvider receive data from ip: " + clientAddress +
                            ", clientPort: " + clientPort + ", isValid: " + isValid);

                    //3. 判断是否合法客户端
                    if (!isValid) {
                        continue;
                    }

                    //4. 读取命令字，和回送端口
                    int index = UDPConstants.HEADER.length;
                    short cmd = (short) (receivePacketData[index++] << 8 | (receivePacketData[index++] & 0xff));
                    int responsePort = receivePacketData[index++] << 24 |
                            ((receivePacketData[index++] & 0xff) << 16) |
                            ((receivePacketData[index++] & 0xff) << 8) |
                            ((receivePacketData[index] & 0xff));

                    if (cmd == UDPConstants.CMD_SEARCH_SERVER_REQ && responsePort > 0) {
                        //5. 构建回送消息
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        buffer.put(UDPConstants.HEADER);
                        buffer.putShort(UDPConstants.CMD_SEARCH_SERVER_RESP);
                        buffer.putInt(tcpServerPort);
                        buffer.put(tcpServerSn.getBytes());
                        DatagramPacket sendPacket = new DatagramPacket(bytes, buffer.position()
                                , receivePacket.getAddress(), responsePort);

                        ds.send(sendPacket);
                        PrintUtil.println("UDPProvider response to: %s tcpServerPort: %d", clientAddress, responsePort);
                    } else {
                        PrintUtil.println("UDPProvider receive cmd nonsupport, cmd: %d, responsePort: %d", cmd, responsePort);
                    }
                }

            } catch (IOException ignore) {
            } finally {
                close();
            }
            PrintUtil.println("UDPProvider finished");
        }

        private void close() {
            if (ds != null) {
                ds.close();
                ds = null;
            }
        }


        public void exit() {
            done = true;
            close();
        }
    }

}
