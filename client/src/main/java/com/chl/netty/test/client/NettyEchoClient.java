package com.chl.netty.test.client;

import com.chl.netty.test.client.handler.NettyEchoClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Scanner;

/**
 * @author ChenHanLin 2020/2/27
 */
@Slf4j
public class NettyEchoClient {
    private int serverport;
    private String serverIp;
    Bootstrap b = new Bootstrap();

    public NettyEchoClient(String ip, int serverport) {
        this.serverIp = ip;
        this.serverport = serverport;
    }

    public void runClient() {
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
        try {
            b.group(workerLoopGroup);
            b.channel(NioSocketChannel.class);
            b.remoteAddress(serverIp, serverport);
            b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(NettyEchoClientHandler.INSTANCE);
                }
            });
            ChannelFuture f = b.connect();
            f.addListener((ChannelFuture futureListener) -> {
                if (futureListener.isSuccess()) {
                    log.info("EchoClient客户端连接成功");
                } else {
                    log.info("EchoClient客户端连接失败");
                }
            });
            //阻塞，直到连接成功
            f.sync();
            Channel channel = f.channel();
            Scanner scanner = new Scanner(System.in);
            System.out.println("请输入内容");
            while (scanner.hasNext()) {
                String next = scanner.next();
                byte[] bytes = (LocalDateTime.now() + ">>" + next).getBytes(StandardCharsets.UTF_8);
                ByteBuf buf = channel.alloc().buffer();
                buf.writeBytes(bytes);
                channel.writeAndFlush(buf);
                System.out.println("请输入内容");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerLoopGroup.shutdownGracefully();
        }
    }
}
