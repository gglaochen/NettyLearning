package com.chl.netty.demo.server.utils;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.GsonBuilder;

/**
 * @author ChenHanLin 2020/2/27
 */
public class JsonUtils {
    static GsonBuilder gb = new GsonBuilder();

    static {
        gb.disableHtmlEscaping();
    }

    /**
     * 使用Gson进行序列化
     */
    public static String pojoToJson(Object obj) {
        return gb.create().toJson(obj);
    }

    /**
     * 使用FastJson进行反序列化
     */
    public static <T> T jsonToPojo(String json, Class<T> tClass) {
        return JSONObject.parseObject(json, tClass);
    }
}
