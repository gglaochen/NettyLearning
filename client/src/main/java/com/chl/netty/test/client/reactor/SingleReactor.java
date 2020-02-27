package com.chl.netty.test.client.reactor;

import com.chl.netty.test.client.reactor.handler.SingleReactorHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

/**
 * @author ChenHanLin 2020/2/27
 */
public class SingleReactor implements Runnable {

    Selector selector;
    ServerSocketChannel serverSocket;

    SingleReactor() throws IOException {
        //...获取选择器、开启 serverSocket 服务监听通道
        this.selector = Selector.open();
        this.serverSocket = ServerSocketChannel.open();
        //需要注意，注册到选择器的通道必须是非阻塞模式
        serverSocket.configureBlocking(false);
        serverSocket.bind(new InetSocketAddress(10002));
        SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        //...绑定AcceptorHandler 新连接处理器到selectKey
        sk.attach(new AcceptorHandler());
    }

    //轮询和分发事件
    @Override
    public void run() {
        try {
            //线程不被中断就一直轮询
            while (!Thread.interrupted()) {
                selector.select();
                Set<SelectionKey> selected = selector.selectedKeys();
                for (SelectionKey sk : selected) {
                    //反应器负责 dispatch 收到的事件
                    dispatch(sk);
                }
                selected.clear();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void dispatch(SelectionKey sk) {
        Runnable handler = (Runnable) sk.attachment();
        //调用之前 attach 绑定到选择键的 handler 处理器对象
        if (handler != null) {
            handler.run();//因为是run不是start所以不会创建新线程
        }
    }

    /**
     * Handler:新连接处理器
     */
    class AcceptorHandler implements Runnable {

        @Override
        public void run() {
            try {
                SocketChannel channel = serverSocket.accept();
                if (channel != null) {
                    new SingleReactorHandler(selector, channel);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
