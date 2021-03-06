package io.choerodon.agile.api.dto;

import com.alibaba.fastjson.JSONObject;
import io.choerodon.agile.infra.common.utils.StringUtil;

import java.io.Serializable;

/**
 * @author dinghuang123@gmail.com
 * @since 2018/7/26
 */
public class PieChartDTO implements Serializable {

    private String name;

    private String typeName;

    private Integer value;

    private Double percent;

    private JSONObject jsonObject;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public Double getPercent() {
        return percent;
    }

    public void setPercent(Double percent) {
        this.percent = percent;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public void setJsonObject(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    @Override
    public String toString() {
        return StringUtil.getToString(this);
    }

}
