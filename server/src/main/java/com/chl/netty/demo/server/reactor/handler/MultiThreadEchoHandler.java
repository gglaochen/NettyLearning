package com.chl.netty.demo.server.reactor.handler;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ChenHanLin 2020/2/27
 */
@Slf4j
public class MultiThreadEchoHandler implements Runnable {

    final SocketChannel channel;
    final SelectionKey sk;
    final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    static final int RECEIVING = 0, SENDING = 1;
    int state = RECEIVING;
    //引入线程池
    static ExecutorService pool = Executors.newFixedThreadPool(4);

    public MultiThreadEchoHandler(Selector selector, SocketChannel c) throws IOException {
        this.channel = c;
        c.configureBlocking(false);
        //将通道注册到选择器上
        this.sk = channel.register(selector, RECEIVING);
        //将本Handler绑定到选择键上
        sk.attach(this);
        //向sk选择键注册Read就绪事件
        sk.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }

    @Override
    public void run() {
        //异步任务，在独立的线程池中执行
        pool.execute(new AsyncTask());
    }

    class AsyncTask implements Runnable {
        @Override
        public void run() {
            MultiThreadEchoHandler.this.asyncRun();
        }
    }

    public synchronized void asyncRun() {
        try {
            if (state == SENDING) {
                //写入通道
                channel.write(byteBuffer);
                //写完后，准备开始从通道读，byteBuffer切换成写入模式
                byteBuffer.clear();
                //写完后，切换为接收的状态
                state = RECEIVING;
            } else if (state == RECEIVING) {
                //从通道读
                int length = 0;
                while ((length = channel.read(byteBuffer)) > 0) {
                    log.info(new String(byteBuffer.array(), 0, length));
                }
                //读完后，准备开始写入通道，buteBuffer 切换成读取模式
                byteBuffer.flip();
                //注册write就绪事件
                sk.interestOps(SelectionKey.OP_WRITE);
                //读完后，进入发送状态
                state = SENDING;
            }
            //处理结束后不能关闭selectKey，需要重复使用
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}