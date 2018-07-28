package io.choerodon.agile.api.controller.v1;

import io.choerodon.agile.api.dto.*;
import io.choerodon.agile.app.service.ReportService;
import io.choerodon.agile.infra.dataobject.EpicChartDO;
import io.choerodon.agile.infra.dataobject.EpicChartListDO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.InitRoleCode;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.CustomPageRequest;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author dinghuang123@gmail.com
 * @since 2018/6/19
 */
@RestController
@RequestMapping(value = "/v1/projects/{project_id}/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    private static final String QUERY_ISSUE_ERROR = "error.issue.query";
    private static final String VERSION_LINE_CHART_ERROR = "error.version.lineChart";

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @ApiOperation("查询冲刺对应的燃尽图报告信息")
    @GetMapping(value = "/{sprintId}/burn_down_report")
    public ResponseEntity<List<ReportIssueDTO>> queryBurnDownReport(@ApiParam(value = "项目id", required = true)
                                                                    @PathVariable(name = "project_id") Long projectId,
                                                                    @ApiParam(value = "sprintId", required = true)
                                                                    @PathVariable Long sprintId,
                                                                    @ApiParam(value = "类型(storyPoints、remainingEstimatedTime、issueCount)", required = true)
                                                                    @RequestParam String type) {
        return Optional.ofNullable(reportService.queryBurnDownReport(projectId, sprintId, type))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException("error.report.queryBurnDownReport"));
    }

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @ApiOperation("查看项目累积流量图")
    @PostMapping(value = "/cumulative_flow_diagram")
    public ResponseEntity<List<CumulativeFlowDiagramDTO>> queryCumulativeFlowDiagram(@ApiParam(value = "项目id", required = true)
                                                                                     @PathVariable(name = "project_id") Long projectId,
                                                                                     @ApiParam(value = "过滤条件", required = true)
                                                                                     @RequestBody CumulativeFlowFilterDTO cumulativeFlowFilterDTO) {
        return Optional.ofNullable(reportService.queryCumulativeFlowDiagram(projectId, cumulativeFlowFilterDTO))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException("error.report.queryCumulativeFlowDiagram"));
    }

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @CustomPageRequest
    @ApiOperation(value = "根据状态查版本下issue列表")
    @GetMapping(value = "/{versionId}/issues")
    public ResponseEntity<Page<IssueListDTO>> queryIssueByOptions(@ApiParam(value = "项目id", required = true)
                                                                  @PathVariable(name = "project_id") Long projectId,
                                                                  @ApiParam(value = "版本id", required = true)
                                                                  @PathVariable Long versionId,
                                                                  @ApiParam(value = "状态", required = true)
                                                                  @RequestParam String status,
                                                                  @ApiParam(value = "类型", required = true)
                                                                  @RequestParam String type,
                                                                  @ApiParam(value = "分页信息", required = true)
                                                                  @SortDefault(value = "issue_id", direction = Sort.Direction.DESC)
                                                                  @ApiIgnore PageRequest pageRequest) {
        return Optional.ofNullable(reportService.queryIssueByOptions(projectId, versionId, status, type, pageRequest))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException(QUERY_ISSUE_ERROR));
    }

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @ApiOperation(value = "版本报告图信息")
    @GetMapping(value = "/{versionId}")
    public ResponseEntity<Map<String, Object>> queryVersionLineChart(@ApiParam(value = "项目id", required = true)
                                                                     @PathVariable(name = "project_id") Long projectId,
                                                                     @ApiParam(value = "版本id", required = true)
                                                                     @PathVariable Long versionId,
                                                                     @ApiParam(value = "统计类型", required = true)
                                                                     @RequestParam String type) {
        return Optional.ofNullable(reportService.queryVersionLineChart(projectId, versionId, type))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException(VERSION_LINE_CHART_ERROR));
    }

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @ApiOperation(value = "速度图")
    @GetMapping(value = "/velocity_chart")
    public ResponseEntity<List<VelocitySprintDTO>> queryVelocityChart(@ApiParam(value = "项目id", required = true)
                                                                      @PathVariable(name = "project_id") Long projectId,
                                                                      @ApiParam(value = "统计类型", required = true)
                                                                      @RequestParam String type) {
        return Optional.ofNullable(reportService.queryVelocityChart(projectId, type))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException("error.velocityChart.get"));
    }

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @ApiOperation("查询饼图")
    @GetMapping(value = "/pie_chart")
    public ResponseEntity<List<PieChartDTO>> queryPieChart(@ApiParam(value = "项目id", required = true)
                                                           @PathVariable(name = "project_id") Long projectId,
                                                           @ApiParam(value = "字段名称:assignee、component、issueType、" +
                                                                   "fixVersion、priority、status、sprint、epic、resolution", required = true)
                                                           @RequestParam String fieldName) {
        return Optional.ofNullable(reportService.queryPieChart(projectId, fieldName))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException("error.report.queryPieChart"));
    }

    @ApiOperation(value = "史诗图")
    @GetMapping(value = "/epic_chart")
    public ResponseEntity<List<EpicChartDO>> queryEpicChart(@ApiParam(value = "项目id", required = true)
                                                                      @PathVariable(name = "project_id") Long projectId,
                                                            @ApiParam(value = "epic id", required = true)
                                                                      @RequestParam Long epicId,
                                                            @ApiParam(value = "统计类型", required = true)
                                                                      @RequestParam String type) {
        return Optional.ofNullable(reportService.queryEpicChart(projectId, epicId, type))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException("error.epicChart.get"));
    }

    @Permission(level = ResourceLevel.PROJECT, roles = {InitRoleCode.PROJECT_MEMBER, InitRoleCode.PROJECT_OWNER})
    @ApiOperation(value = "史诗图问题列表")
    @GetMapping(value = "/epic_issue_list")
    public ResponseEntity<List<EpicChartListDO>> queryEpicChartList(@ApiParam(value = "项目id", required = true)
                                                            @PathVariable(name = "project_id") Long projectId,
                                                                    @ApiParam(value = "epic id", required = true)
                                                            @RequestParam Long epicId) {
        return Optional.ofNullable(reportService.queryEpicChartList(projectId, epicId))
                .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
                .orElseThrow(() -> new CommonException("error.epicChartList.get"));
    }

}
