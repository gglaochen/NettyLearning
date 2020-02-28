package com.chl.netty.demo.server.reactor;

import com.chl.netty.demo.server.reactor.handler.MultiThreadEchoHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ChenHanLin 2020/2/27
 */
@Slf4j
public class MultiThreadReactor {

    ServerSocketChannel serverSocket;
    AtomicInteger next = new AtomicInteger(0);
    //选择器集合，引入多个选择器
    Selector[] selectors = new Selector[2];
    //引入多个子反应器
    SubReactor[] subReactors = null;

    public MultiThreadReactor() throws IOException {
        //初始化多个选择器
        selectors[0] = Selector.open();
        selectors[1] = Selector.open();
        serverSocket = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10003);
        serverSocket.socket().bind(address);
        //非阻塞
        serverSocket.configureBlocking(false);
        //第一个选择器，负责监控新连接事件
        SelectionKey sk = serverSocket.register(selectors[0], SelectionKey.OP_ACCEPT);
        //绑定Handler: attach 新连接监控 handler 处理器到 SelectionKey（选择键）
        sk.attach(new AcceptorHandler());
        //第一个子反应器，对应一个选择器
        SubReactor subReactorl = new SubReactor(selectors[0]);
        SubReactor subReactor2 = new SubReactor(selectors[1]);
        this.subReactors = new SubReactor[]{subReactorl, subReactor2};
    }

    public void startService() {
        //一个子反应器对应一个线程
        new Thread(subReactors[0]).start();
        new Thread(subReactors[1]).start();
    }

    //子反应器
    public class SubReactor implements Runnable {
        //每个子反应器负责一个选择器的查询和选择
        final Selector selector;

        public SubReactor(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    selector.select();
                    Set<SelectionKey> keySet = selector.selectedKeys();
                    for (SelectionKey sk : keySet) {
                        //反应器负责dispatch收到的事件
                        dispatch(sk);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        void dispatch(SelectionKey sk) {
            Runnable handler = (Runnable) sk.attachment();
            if (handler != null) {
                handler.run();
            }
        }

    }

    //Handler：新连接处理器
    public class AcceptorHandler implements Runnable {
        @Override
        public void run() {
            try {
                SocketChannel channel = serverSocket.accept();
                if (channel != null) {
                    new MultiThreadEchoHandler(selectors[next.get()], channel);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (next.incrementAndGet() == selectors.length) {//如果原子整数自增到选择器长度置0
                next.set(0);
            }
        }
    }
}