package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.core.Connector;
import com.robert.link.core.IoContext;
import com.robert.link.handler.ConnectorCloseChain;
import com.robert.link.handler.ConnectorHandler;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.link.impl.ScheduleImpl;
import com.robert.util.CloseUtils;
import com.robert.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientTest {

    private static final int CLIENT_SIZE = 2000;
    private static final int SEND_THREAD_SIZE = 4;
    private static final int SEND_THREAD_DELAY = 500;
    private static volatile boolean done;


    public static void main(String[] args) throws IOException {
        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        if (serverInfo == null) {
            return;
        }
        File cachePath = FileUtils.getCacheDir("client/test");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .scheduler(new ScheduleImpl(1))
                .start();

        int size = 0;
        final List<TcpClient> clients = new ArrayList<>(CLIENT_SIZE);

        final ConnectorCloseChain closeChain = new ConnectorCloseChain() {
            @Override
            protected boolean consume(ConnectorHandler handler, Connector connector) {
                //noinspection SuspiciousMethodCalls
                clients.remove(handler);
                if (clients.size() == 0) {
                    CloseUtils.close(System.in);
                }
                return false;
            }
        };

        for (int i = 0; i < CLIENT_SIZE; i++) {
            try {
                TcpClient tcpClient = TcpClient.startConnect(serverInfo, cachePath, false);
                if (tcpClient == null) {
                    throw new NullPointerException();
                }
                tcpClient.getCloseChain().appendLast(closeChain);
                clients.add(tcpClient);
                System.out.println("连接成功：" + (++size));
            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
                break;
            }
        }

        System.in.read();

        Runnable runnable = () -> {
            while (!done) {
                TcpClient[] tcpClients = clients.toArray(new TcpClient[0]);
                for (TcpClient tcpClient : tcpClients) {
                    tcpClient.send("Hello~");
                }
                if (SEND_THREAD_DELAY > 0) {
                    try {
                        Thread.sleep(SEND_THREAD_DELAY);
                    } catch (InterruptedException ignore) {

                    }
                }
            }
        };

        List<Thread> threads = new ArrayList<>(SEND_THREAD_SIZE);
        for (int i = 0; i < SEND_THREAD_SIZE; i++) {
            Thread thread = new Thread(runnable);
            thread.start();
            threads.add(thread);
        }

        System.in.read();

        done = true;

        TcpClient[] tcpClients = clients.toArray(new TcpClient[0]);
        for (TcpClient tcpClient : tcpClients) {
            tcpClient.exit();
        }

        IoContext.close();

        for (Thread thread : threads) {
            try {
                thread.interrupt();
            } catch (Exception ignore) {
            }
        }
    }
}
