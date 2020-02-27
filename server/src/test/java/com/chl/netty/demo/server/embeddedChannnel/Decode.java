package com.chl.netty.demo.server.embeddedChannnel;

import com.chl.netty.demo.server.ServerApplicationTests;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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

    /**
     * 使用netty自带LengthFieldBasedFrameDecoder解码器解决分包问题
     */
    @Test
    public void testLengthFieldBasedFrameDecoder1() {
        try {
            /**参数按顺序分别是
             发送的数据包最大长度
             长度字段偏移量：长度字段位于数据包的字节数组的下表位置
             长度字段自己占用的字节数：长度字段占用的字节数
             长度字段的偏移量矫正：在传输协议比较复杂的情况下，例如包含了长度字段、协议版本号、魔数等等，那么，解码就需要进行长度矫正：矫正值的计算公式为：内容字段偏移量-长度字段偏移量-长度字段的字节数
             丢弃的起始字节数：一些起辅助作用的字段，最终结果不需要，比如前面的长度字段
             **/
            final LengthFieldBasedFrameDecoder spliter = new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4);
            ChannelInitializer i = new ChannelInitializer<EmbeddedChannel>() {
                protected void initChannel(EmbeddedChannel ch) {
                    ch.pipeline().addLast(spliter);
                    ch.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
                    ch.pipeline().addLast(new StringHandler());
                }
            };
            EmbeddedChannel channel = new EmbeddedChannel(i);
            for (int j = 1; j <= 100; j++) {
                ByteBuf buf = Unpooled.buffer();
                String s = j + "次发送";
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
                channel.writeInbound(buf);
            }
            Thread.sleep(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public class StringHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println((String) msg);
        }
    }
}
