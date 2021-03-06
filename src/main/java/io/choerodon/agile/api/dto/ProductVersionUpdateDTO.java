package io.choerodon.agile.api.dto;

import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * Created by jian_zhang02@163.com on 2018/5/14.
 */

public class ProductVersionUpdateDTO {
    private static final String PROJECT_ID_NULL_ERROR = "error.projectId.NotNull";
    private static final String VERSION_ID_NULL_ERROR = "error.versionId.NotNull";
    private static final String OBJECT_VERSION_NUMBER_NULL_ERROR = "error.objectVersionNumber.NotNull";

    @NotNull(message = VERSION_ID_NULL_ERROR)
    private Long versionId;
    private String name;
    private String description;
    private Date startDate;
    private Date releaseDate;
    @NotNull(message = PROJECT_ID_NULL_ERROR)
    private Long projectId;
    @NotNull(message = OBJECT_VERSION_NUMBER_NULL_ERROR)
    private Long objectVersionNumber;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getObjectVersionNumber() {
        return objectVersionNumber;
    }

    public void setObjectVersionNumber(Long objectVersionNumber) {
        this.objectVersionNumber = objectVersionNumber;
    }
}
