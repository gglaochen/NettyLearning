package com.chl.netty.test.client.netty;

import com.chl.netty.test.client.netty.entity.JsonMsg;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ChenHanLin 2020/2/28
 */
@Slf4j
public class JsonSendClient {

    Bootstrap b = new Bootstrap();

    public void runClient() {
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
        try {
            b.group(workerLoopGroup);
            b.channel(NioSocketChannel.class);
            b.remoteAddress("127.0.0.1", 10011);
            b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new LengthFieldPrepender(4));
                    ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
                }
            });
            ChannelFuture channelFuture = b.connect();
            channelFuture.addListener((ChannelFuture futureListener) -> {
                if (futureListener.isSuccess()) {
                    log.info("EchoClient客户端连接成功");
                } else {
                    log.info("EchoClient客户端连接失败");
                }
            });
            channelFuture.sync();
            Channel channel = channelFuture.channel();
            JsonMsg jsonMsg = JsonMsg.builder()
                    .id(123)
                    .content("发送的内容")
                    .build();
            channel.writeAndFlush(jsonMsg.convertToJson());
            log.info("发送json数据:{}", jsonMsg);
            ChannelFuture channelFuture1 = channel.closeFuture();
            channelFuture1.sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerLoopGroup.shutdownGracefully();
        }
    }
}
