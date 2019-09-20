package com.robert.sample.client;

import com.robert.util.CloseUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class TcpClient {

    private final int port;
    private final String host;
    private Socket socket;

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            ReadHandler readHandler = new ReadHandler(socket.getInputStream());
            readHandler.start();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void exit() {
        CloseUtils.close(socket);
    }

    private final class ReadHandler extends Thread {

        private final InputStream inputStream;

        public ReadHandler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {


        }
    }

}
