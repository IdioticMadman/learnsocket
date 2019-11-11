package com.robert.client;

import com.robert.Commands;
import com.robert.Constants;
import com.robert.client.bean.ServerInfo;
import com.robert.link.box.FileSendPacket;
import com.robert.link.core.Connector;
import com.robert.link.core.IoContext;
import com.robert.link.core.schedule.IdleTimeoutSchedule;
import com.robert.link.handler.ConnectorCloseChain;
import com.robert.link.handler.ConnectorHandler;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.link.impl.ScheduleImpl;
import com.robert.util.CloseUtils;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Client {

    public static void main(String[] args) throws IOException {

        File cachePath = FileUtils.getCacheDir("client");

        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        if (serverInfo != null) {
            IoContext.setup()
                    .ioProvider(new IoSelectorProvider())
                    .scheduler(new ScheduleImpl(1))
                    .start();
            TcpClient tcpClient = null;
            try {
                tcpClient = TcpClient.startConnect(serverInfo, cachePath);
                if (tcpClient != null) {
                    tcpClient.getCloseChain().appendLast(new ConnectorCloseChain() {
                        @Override
                        protected boolean consume(ConnectorHandler handler, Connector connector) {
                            CloseUtils.close(System.in);
                            return true;
                        }
                    });
                    tcpClient.schedule(new IdleTimeoutSchedule(10, TimeUnit.SECONDS, tcpClient));
                    chat(tcpClient);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (tcpClient != null) {
                    tcpClient.exit();
                }
            }
            IoContext.close();
        } else {
            PrintUtil.println("未查找到server");
        }
    }

    private static void chat(TcpClient tcpClient) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        do {
            try {
                String line = reader.readLine();
                if (line == null) continue;
                if (Constants.COMMAND_EXIT.equalsIgnoreCase(line)) break;
                if (line.startsWith("--f")) {
                    String[] args = line.split(" ");
                    if (args.length >= 2) {
                        String filePath = args[1];
                        File sendFile = new File(filePath);
                        if (sendFile.exists() && sendFile.isFile()) {
                            FileSendPacket packet = new FileSendPacket(sendFile);
                            tcpClient.send(packet);
                            continue;
                        }
                    }
                }
                tcpClient.send(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (true);
    }
}
