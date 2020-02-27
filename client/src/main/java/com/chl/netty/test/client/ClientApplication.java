package com.chl.netty.test.client;

import com.chl.netty.test.client.netty.NettyEchoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
        new NettyEchoClient("127.0.0.1",10001).runClient();
    }

}
