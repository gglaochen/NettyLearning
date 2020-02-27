package com.chl.netty.demo.server;

import com.chl.netty.demo.server.netty.NettyEchoServer;
import com.chl.netty.demo.server.reactor.MultiThreadReactor;
import com.chl.netty.demo.server.reactor.SingleReactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@Slf4j
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
        new NettyEchoServer(10001).runServer();
        try {
            new Thread(new SingleReactor()).start();
            MultiThreadReactor multiThreadReactor = new  MultiThreadReactor();
            multiThreadReactor.startService();
        }catch (IOException e){
            log.info("IO Error");
        }
    }

}
