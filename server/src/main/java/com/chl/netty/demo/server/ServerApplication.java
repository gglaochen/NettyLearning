package com.chl.netty.demo.server;

import com.chl.netty.demo.server.netty.JsonServer;
import com.chl.netty.demo.server.reactor.MultiThreadReactor;
import com.chl.netty.demo.server.reactor.SingleReactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
        try {
            /* netty demo*/
//        new NettyEchoServer(10001).runServer();
            /* 单线程Reactor反应器模式*/
//            new Thread(new SingleReactor()).start();
            /* 多线程Reactor反应器模式*/
//            MultiThreadReactor multiThreadReactor = new  MultiThreadReactor();
//            multiThreadReactor.startService();
            /* json 协议传输*/
            new JsonServer().runServer();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
