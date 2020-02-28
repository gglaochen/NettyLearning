package com.chl.netty.test.client.netty.entity;

import com.chl.netty.test.client.utils.JsonUtils;
import lombok.Builder;
import lombok.Data;

/**
 * @author ChenHanLin 2020/2/28
 */
@Data
@Builder
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
