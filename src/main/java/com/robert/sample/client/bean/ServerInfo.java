package com.robert.sample.client.bean;

import java.net.InetAddress;

public class ServerInfo {

    private String sn;
    private InetAddress address;
    private int port;

    public ServerInfo(String sn, InetAddress address, int port) {
        this.sn = sn;
        this.address = address;
        this.port = port;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "ServerInfo{" +
                "sn='" + sn + '\'' +
                ", address=" + address +
                ", port=" + port +
                '}';
    }
}
