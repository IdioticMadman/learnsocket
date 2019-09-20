package com.robert.sample;

import com.robert.sample.client.UDPSearcher;
import com.robert.sample.client.bean.ServerInfo;

public class Client {

    public static void main(String[] args) {

        ServerInfo serverInfo = UDPSearcher.searchServer(3000);
        System.out.println(serverInfo);
    }
}
