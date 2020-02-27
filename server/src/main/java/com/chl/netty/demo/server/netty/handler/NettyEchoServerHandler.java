package com.chl.netty.demo.server.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * @author ChenHanLin 2020/2/27
 */
@Slf4j
@ChannelHandler.Sharable
public class NettyEchoServerHandler extends ChannelInboundHandlerAdapter {
    public static final NettyEchoServerHandler INSTANCE = new NettyEchoServerHandler();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        //Netty4.1开始，ByteBuf的默认类型是直接内存缓冲区
        log.info("缓冲区类型:{}", in.hasArray() ? "堆缓冲区" : "直接缓冲区");
        int len = in.readableBytes();
        byte[] arr = new byte[len];
        //使用get方式数据在msg中还保留
        in.getBytes(0, arr);
        log.info("服务端接收到：{}", new String(arr, StandardCharsets.UTF_8));
        log.info("写回前，引用计数：{}", ((ByteBuf) msg).refCnt());
        //写回数据，异步任务
        ChannelFuture f = ctx.writeAndFlush(msg);
        f.addListener((ChannelFuture futureListener) -> log.info("写回后，引用计数:{}", ((ByteBuf) msg).refCnt()));
    }
}