package io.choerodon.agile.infra.dataobject;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  16:26 2018/9/4
 * Description:
 */
public class IssueTypeDistributeDO {
    private String typeCode;
    private Integer issueNum;
    private Double percent;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public Integer getIssueNum() {
        return issueNum;
    }

    public void setIssueNum(Integer issueNum) {
        this.issueNum = issueNum;
    }

    public Double getPercent() {
        return percent;
    }

    public void setPercent(Double percent) {
        this.percent = percent;
    }
}