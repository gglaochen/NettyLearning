package com.chl.netty.demo.server.embeddedChannnel;

import com.chl.netty.demo.server.ServerApplicationTests;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author ChenHanLin 2020/2/27
 */
@Slf4j
public class Decode extends ServerApplicationTests {

    @Test
    public void test() {
        ChannelInitializer i = new ChannelInitializer<EmbeddedChannel>() {
            protected void initChannel(EmbeddedChannel ch) {
                ch.pipeline().addLast(new Byte2IntegerDecoder());//先解码
                ch.pipeline().addLast(new IntegerProcessHandler());//后处理
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(i);
        for (int j = 0; j < 100; j++) {
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(j);
            channel.writeInbound(buf);
        }
        try {
            Thread.sleep(Integer.MAX_VALUE);
        } catch (Exception e) {
        }
    }

    public static class IntegerProcessHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Integer integer = (Integer) msg;
            log.info("打印出一个整数:{}", integer);
        }
    }

    public static class Byte2IntegerDecoder extends ByteToMessageDecoder {
        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (in.readableBytes() >= 4) {
                int i = in.readInt();
                log.info("解码出一个整数{}", i);
                out.add(i);
            }
        }
    }
}
