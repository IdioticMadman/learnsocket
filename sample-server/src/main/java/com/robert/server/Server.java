package com.robert.server;

import com.robert.Commands;
import com.robert.Constants;
import com.robert.FooGui;
import com.robert.common.TCPConstants;
import com.robert.link.core.IoContext;
import com.robert.link.impl.IoSelectorProvider;
import com.robert.link.impl.IoStealingSelectorProvider;
import com.robert.link.impl.ScheduleImpl;
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
                .ioProvider(new IoStealingSelectorProvider(3))
                .scheduler(new ScheduleImpl(1))
                .start();
        TcpServer tcpServer = new TcpServer(TCPConstants.PORT_SERVER, cacheDir);
        boolean started = tcpServer.startServer();
        if (!started) {
            PrintUtil.println("TCP 服务启动失败！");
            return;
        }

        UDPProvider.start();

        FooGui gui = new FooGui("Clink_Server", tcpServer::getStatusString);
        gui.doShow();

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
        } while (!Constants.COMMAND_EXIT.equalsIgnoreCase(line));

        gui.doDismiss();
        UDPProvider.stop();
        tcpServer.stop();
        IoContext.close();
    }
}
