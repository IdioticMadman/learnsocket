package com.robert.server;

import com.robert.common.TCPConstants;
import com.robert.link.core.IoContext;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.util.FileUtils;
import com.robert.util.PrintUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {

    public static void main(String[] args) throws IOException {
        File cacheDir = FileUtils.getCacheDir("server");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();
        TcpServer tcpServer = new TcpServer(TCPConstants.PORT_SERVER, cacheDir);
        boolean started = tcpServer.startServer();
        if (!started) {
            PrintUtil.println("TCP 服务启动失败！");
            return;
        }

        UDPProvider.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = null;
        do {
            try {
                line = reader.readLine();
                if (line == null) continue;
                tcpServer.broadcast(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!"00bye00".equalsIgnoreCase(line));

        UDPProvider.stop();
        tcpServer.stop();
        IoContext.close();
    }
}
