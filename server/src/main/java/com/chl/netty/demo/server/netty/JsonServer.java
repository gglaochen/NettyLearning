package com.chl.netty.demo.server.netty;

import com.chl.netty.demo.server.netty.entity.JsonMsg;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ChenHanLin 2020/2/28
 */
@Slf4j
public class JsonServer {

    private ServerBootstrap b = new ServerBootstrap();

    public void runServer() {
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerLoopGroup = new NioEventLoopGroup();
        try {
            b.group(bossLoopGroup, workerLoopGroup);
            b.localAddress(10011);
            b.channel(NioServerSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4));
                    ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8));
                    ch.pipeline().addLast(new JsonMsgDecoder());
                }
            });
            ChannelFuture f = b.bind().sync();
            f.addListener((ChannelFuture futureListener) -> {
                if (futureListener.isSuccess()) {
                    log.info("服务成功启动,监听的端口为{}",futureListener.channel().localAddress());
                } else {
                    log.info("服务启动失败");
                }
            });
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerLoopGroup.shutdownGracefully();
            bossLoopGroup.shutdownGracefully();
        }
    }

    static class JsonMsgDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String json = (String) msg;
            JsonMsg jsonMsg = JsonMsg.toMsg(json);
            log.info("收到的POJO：{}", jsonMsg);
            super.channelRead(ctx, msg);
        }
    }
}
