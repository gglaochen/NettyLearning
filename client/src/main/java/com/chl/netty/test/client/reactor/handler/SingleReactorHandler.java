package com.chl.netty.test.client.reactor.handler;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * @author ChenHanLin 2020/2/27
 */
@Slf4j
public class SingleReactorHandler implements Runnable {
    final SocketChannel channel;
    final SelectionKey sk;
    final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    static final int RECEVING = 0, SENDING = 1;
    int state = RECEVING;

    public SingleReactorHandler(Selector selector, SocketChannel c) throws IOException {
        this.channel = c;
        c.configureBlocking(false);
        //获取选择键，再设置感兴趣的IO事件，这里注册到和反应器同一个选择器中
        this.sk = channel.register(selector, RECEVING);
        //将 Handler 自身作为选择器的附件
        sk.attach(this);
        //注册Read就绪事件
        sk.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }

    @Override
    public void run() {
        try {
            if (state == SENDING) {
                //写入通道
                channel.write(byteBuffer);
                //写完后，准备开始从通道读，byteBuffer切换成写入模式
                byteBuffer.clear();
                //写完后，切换为接收的状态
                state = RECEVING;
            } else if (state == RECEVING) {
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