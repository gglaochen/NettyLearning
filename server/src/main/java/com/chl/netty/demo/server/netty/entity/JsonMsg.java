package com.chl.netty.demo.server.netty.entity;

import com.chl.netty.demo.server.utils.JsonUtils;
import lombok.Data;

/**
 * @author ChenHanLin 2020/2/28
 */
@Data
public class JsonMsg {
    private int id;
    private String content;

    public String convertToJson() {
        return JsonUtils.pojoToJson(this);
    }

    public static JsonMsg toMsg(String json) {
        return JsonUtils.jsonToPojo(json, JsonMsg.class);
    }
}
