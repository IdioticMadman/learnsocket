package com.robert.client;

import com.robert.client.bean.ServerInfo;
import com.robert.link.box.FileSendPacket;
import com.robert.link.core.IoContext;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Client {

    public static void main(String[] args) throws IOException {

        File cachePath = FileUtils.getCacheDir("client");

        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        if (serverInfo != null) {
            IoContext.setup()
                    .ioProvider(new IoSelectorProvider())
                    .start();
            TcpClient tcpClient = null;
            try {
                tcpClient = TcpClient.startConnect(serverInfo, cachePath);
                chat(tcpClient);
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
                if ("00bye00".equalsIgnoreCase(line)) break;
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
