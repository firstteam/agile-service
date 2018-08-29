package io.choerodon.agile.app.service.impl;

import com.alibaba.fastjson.JSON;
import io.choerodon.agile.api.dto.*;
import io.choerodon.agile.app.assembler.*;
import io.choerodon.agile.domain.agile.converter.ProductVersionConverter;
import io.choerodon.agile.domain.agile.event.VersionPayload;
import io.choerodon.agile.domain.agile.rule.ProductVersionRule;
import io.choerodon.agile.infra.common.utils.RedisUtil;
import io.choerodon.agile.infra.dataobject.IssueCountDO;
import io.choerodon.agile.infra.dataobject.VersionIssueChangeDO;
import io.choerodon.agile.infra.dataobject.VersionIssueDO;
import io.choerodon.asgard.saga.annotation.Saga;
import io.choerodon.asgard.saga.dto.StartInstanceDTO;
import io.choerodon.asgard.saga.feign.SagaClient;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.agile.app.service.ProductVersionService;
import io.choerodon.agile.domain.agile.entity.ProductVersionE;
import io.choerodon.agile.domain.agile.repository.ProductVersionRepository;
import io.choerodon.agile.domain.agile.repository.VersionIssueRelRepository;
import io.choerodon.agile.infra.common.utils.SearchUtil;
import io.choerodon.agile.infra.common.utils.StringUtil;
import io.choerodon.agile.infra.dataobject.ProductVersionDO;
import io.choerodon.agile.infra.mapper.ProductVersionMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static java.util.stream.Collectors.*;

/**
 * Created by jian_zhang02@163.com on 2018/5/14.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class ProductVersionServiceImpl implements ProductVersionService {

    @Autowired
    private ProductVersionCreateAssembler productVersionCreateAssembler;
    @Autowired
    private ProductVersionUpdateAssembler productVersionUpdateAssembler;
    @Autowired
    private ProductVersionPageAssembler productVersionPageAssembler;
    @Autowired
    private ProductVersionStatisticsAssembler versionStatisticsAssembler;
    @Autowired
    private IssueAssembler issueAssembler;
    @Autowired
    private ProductVersionRepository productVersionRepository;
    @Autowired
    private VersionIssueRelRepository versionIssueRelRepository;
    @Autowired
    private ProductVersionDataAssembler versionDataAssembler;
    @Autowired
    private ProductVersionRule productVersionRule;
    @Autowired
    private ProductVersionMapper productVersionMapper;
    @Autowired
    private ProductVersionConverter productVersionConverter;
    @Autowired
    private RedisUtil redisUtil;

    private static final String SEARCH_ARGS = "searchArgs";
    public static final String ADVANCE_SEARCH_ARGS = "advancedSearchArgs";
    private static final String VERSION_PLANNING = "version_planning";
    private static final String NOT_EQUAL_ERROR = "error.projectId.notEqual";
    private static final String NOT_FOUND = "error.version.notFound";
    private static final String CATEGORY_DONE_CODE = "done";
    private static final String CATEGORY_TODO_CODE = "todo";
    private static final String CATEGORY_DOING_CODE = "doing";
    private static final String VERSION_ARCHIVED_CODE = "archived";
    private static final String REVOKE_ARCHIVED_ERROR = "error.productVersion.revokeArchived";
    private static final String ARCHIVED_ERROR = "error.productVersion.archived";
    private static final String VERSION_STATUS_RELEASE_CODE = "released";
    private static final String REVOKE_RELEASE_ERROR = "error.productVersion.revokeRelease";
    private static final String SOURCE_VERSION_ERROR = "error.sourceVersionIds.notNull";
    private static final String FIX_RELATION_TYPE = "fix";
    private static final String INFLUENCE_RELATION_TYPE = "influence";

    private final SagaClient sagaClient;

    @Autowired
    public ProductVersionServiceImpl(SagaClient sagaClient) {
        this.sagaClient = sagaClient;
    }

    @Saga(code = "agile-create-version", description = "创建版本", inputSchemaClass = VersionPayload.class)
    @Override
    public synchronized ProductVersionDetailDTO createVersion(Long projectId, ProductVersionCreateDTO versionCreateDTO) {
        try {
            if (!projectId.equals(versionCreateDTO.getProjectId())) {
                throw new CommonException(NOT_EQUAL_ERROR);
            }
            ProductVersionE productVersionE = productVersionCreateAssembler.dtoToEntity(versionCreateDTO);
            productVersionE.checkDate();
            productVersionRule.judgeName(productVersionE.getProjectId(), productVersionE.getVersionId(), productVersionE.getName());
            //设置状态
            productVersionE.setStatusCode(VERSION_PLANNING);
            //设置编号
            Integer sequence = productVersionMapper.queryMaxSequenceByProject(projectId);
            productVersionE.setSequence(sequence == null ? 0 : sequence + 1);
            ProductVersionDetailDTO result = new ProductVersionDetailDTO();
            ProductVersionE query = productVersionRepository.createVersion(productVersionE);
            BeanUtils.copyProperties(query, result);
            VersionPayload versionPayload = new VersionPayload();
            versionPayload.setVersionId(query.getVersionId());
            versionPayload.setProjectId(query.getProjectId());
            sagaClient.startSaga("agile-create-version", new StartInstanceDTO(JSON.toJSONString(versionPayload), "", ""));
            return result;
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }

    }

    @Override
    public Boolean deleteVersion(Long projectId, Long versionId, Long fixTargetVersionId, Long influenceTargetVersionId) {
        productVersionRule.judgeExist(projectId, fixTargetVersionId);
        productVersionRule.judgeExist(projectId, influenceTargetVersionId);
        CustomUserDetails customUserDetails = DetailsHelper.getUserDetails();
        if (fixTargetVersionId != null && !Objects.equals(fixTargetVersionId, 0L)) {
            List<VersionIssueDO> versionIssues = productVersionMapper.queryIssuesByRelationType(projectId, versionId, FIX_RELATION_TYPE);
            productVersionRepository.batchIssueToDestination(projectId, fixTargetVersionId, versionIssues, new Date(), customUserDetails.getUserId());
        }
        if (influenceTargetVersionId != null && !Objects.equals(influenceTargetVersionId, 0L)) {
            List<VersionIssueDO> versionIssues = productVersionMapper.queryIssuesByRelationType(projectId, versionId, INFLUENCE_RELATION_TYPE);
            productVersionRepository.batchIssueToDestination(projectId, influenceTargetVersionId, versionIssues, new Date(), customUserDetails.getUserId());
        }
        versionIssueRelRepository.deleteByVersionId(projectId, versionId);
        return simpleDeleteVersion(projectId, versionId);
    }

    @Saga(code = "agile-delete-version", description = "删除版本", inputSchemaClass = VersionPayload.class)
    private Boolean simpleDeleteVersion(Long projectId, Long versionId) {
        try {
            ProductVersionDO versionDO = new ProductVersionDO();
            versionDO.setProjectId(projectId);
            versionDO.setVersionId(versionId);
            ProductVersionE versionE = productVersionCreateAssembler.doToEntity(productVersionMapper.selectOne(versionDO));
            if (versionE == null) {
                throw new CommonException(NOT_FOUND);
            }
            Boolean deleteResult = productVersionRepository.deleteVersion(versionE);
            VersionPayload versionPayload = new VersionPayload();
            versionPayload.setVersionId(versionE.getVersionId());
            versionPayload.setProjectId(versionE.getProjectId());
            sagaClient.startSaga("agile-delete-version", new StartInstanceDTO(JSON.toJSONString(versionPayload), "", ""));
            return deleteResult;
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public ProductVersionDetailDTO updateVersion(Long projectId, Long versionId, ProductVersionUpdateDTO versionUpdateDTO, List<String> fieldList) {
        if (!projectId.equals(versionUpdateDTO.getProjectId())) {
            throw new CommonException(NOT_EQUAL_ERROR);
        }
        ProductVersionE productVersionE = productVersionUpdateAssembler.dtoToEntity(versionUpdateDTO);
        productVersionE.checkDate();
        productVersionRule.judgeName(productVersionE.getProjectId(), productVersionE.getVersionId(), productVersionE.getName());
        productVersionE.setVersionId(versionId);
        return productVersionUpdateAssembler.entityToDto(productVersionRepository.updateVersion(productVersionE, fieldList));
    }

    @Override
    public Page<ProductVersionPageDTO> queryByProjectId(Long projectId, PageRequest pageRequest, Map<String, Object> searchParamMap) {
        //过滤查询和排序
        Map<String, Object> result = SearchUtil.setParam(searchParamMap);
        Page<Long> versionIds = PageHelper.doPageAndSort(pageRequest, () ->
                productVersionMapper.queryVersionIdsByProjectId(projectId, StringUtil.cast(result.get(SEARCH_ARGS)), StringUtil.cast(result.get(ADVANCE_SEARCH_ARGS))));
        Page<ProductVersionPageDTO> versionPage = new Page<>();
        versionPage.setNumber(versionIds.getNumber());
        versionPage.setNumberOfElements(versionIds.getNumberOfElements());
        versionPage.setSize(versionIds.getSize());
        versionPage.setTotalElements(versionIds.getTotalElements());
        versionPage.setTotalPages(versionIds.getTotalPages());
        if ((versionIds.getContent() != null) && !versionIds.isEmpty()) {
            versionPage.setContent(productVersionPageAssembler.doListToDto(productVersionMapper.queryVersionByIds(projectId, versionIds.getContent())));
        }
        return versionPage;
    }

    @Override
    public Boolean repeatName(Long projectId, String name) {
        return productVersionMapper.isRepeatName(projectId, name);
    }

    @Override
    public List<ProductVersionDataDTO> queryVersionByProjectId(Long projectId) {
        List<ProductVersionDataDTO> productVersions = versionDataAssembler.doListToDTO(productVersionMapper.queryVersionByProjectId(projectId));
        if (!productVersions.isEmpty()) {
            List<Long> productVersionIds = productVersions.stream().map(ProductVersionDataDTO::getVersionId).collect(toList());
            Map<Long, Integer> issueCountMap = productVersionMapper.queryIssueCount(projectId, productVersionIds, null).stream().collect(toMap(IssueCountDO::getId, IssueCountDO::getIssueCount));
            Map<Long, Integer> doneIssueCountMap = productVersionMapper.queryIssueCount(projectId, productVersionIds, CATEGORY_DONE_CODE).stream().collect(toMap(IssueCountDO::getId, IssueCountDO::getIssueCount));
            Map<Long, Integer> notEstimateMap = productVersionMapper.queryNotEstimate(projectId, productVersionIds).stream().collect(toMap(IssueCountDO::getId, IssueCountDO::getIssueCount));
            Map<Long, Integer> totalEstimateMap = productVersionMapper.queryTotalEstimate(projectId, productVersionIds).stream().collect(toMap(IssueCountDO::getId, IssueCountDO::getIssueCount));
            productVersions.forEach(productVersion -> {
                productVersion.setIssueCount(issueCountMap.get(productVersion.getVersionId()));
                productVersion.setDoneIssueCount(doneIssueCountMap.get(productVersion.getVersionId()));
                productVersion.setNotEstimate(notEstimateMap.get(productVersion.getVersionId()));
                productVersion.setTotalEstimate(totalEstimateMap.get(productVersion.getVersionId()));
            });
        }
        return productVersions;
    }

    @Override
    public ProductVersionStatisticsDTO queryVersionStatisticsByVersionId(Long projectId, Long versionId) {
        ProductVersionStatisticsDTO productVersionStatistics = versionStatisticsAssembler.doToDto(productVersionMapper.queryVersionStatisticsByVersionId(projectId, versionId));
        productVersionStatistics.setTodoCategoryIssueCount(versionStatisticsAssembler.doListToIssueCountDto(productVersionMapper.queryIssueCountByVersionId(projectId, versionId, CATEGORY_TODO_CODE)));
        productVersionStatistics.setDoingCategoryIssueCount(versionStatisticsAssembler.doListToIssueCountDto(productVersionMapper.queryIssueCountByVersionId(projectId, versionId, CATEGORY_DOING_CODE)));
        productVersionStatistics.setDoneCategoryIssueCount(versionStatisticsAssembler.doListToIssueCountDto(productVersionMapper.queryIssueCountByVersionId(projectId, versionId, CATEGORY_DONE_CODE)));
        return productVersionStatistics;
    }

    @Override
    public List<IssueListDTO> queryIssueByVersionIdAndStatusCode(Long projectId, Long versionId, String statusCode) {
        return issueAssembler.issueDoToIssueListDto(productVersionMapper.queryIssueByVersionIdAndStatusCode(projectId, versionId, statusCode));
    }

    @Override
    public VersionMessageDTO queryReleaseMessageByVersionId(Long projectId, Long versionId) {
        VersionMessageDTO versionReleaseMessage = new VersionMessageDTO();
        versionReleaseMessage.setFixIssueCount(productVersionMapper.queryNotDoneIssueCount(projectId, versionId));
        versionReleaseMessage.setVersionNames(versionStatisticsAssembler.doListToVersionNameDto(productVersionMapper.queryPlanVersionNames(projectId, versionId)));
        return versionReleaseMessage;
    }

    @Override
    public ProductVersionDetailDTO releaseVersion(Long projectId, ProductVersionReleaseDTO productVersionRelease) {
        if (!Objects.equals(projectId, productVersionRelease.getProjectId())) {
            throw new CommonException(NOT_EQUAL_ERROR);
        }
        productVersionRule.isRelease(projectId, productVersionRelease);
        CustomUserDetails customUserDetails = DetailsHelper.getUserDetails();
        if (productVersionRelease.getTargetVersionId() != null && !Objects.equals(productVersionRelease.getTargetVersionId(), 0L)) {
            List<VersionIssueDO> incompleteIssues = productVersionMapper.queryIncompleteIssues(projectId, productVersionRelease.getVersionId());
            if (!incompleteIssues.isEmpty()) {
                versionIssueRelRepository.deleteIncompleteIssueByVersionId(projectId, productVersionRelease.getVersionId());
                productVersionRepository.batchIssueToDestination(projectId, productVersionRelease.getTargetVersionId(), incompleteIssues, new Date(), customUserDetails.getUserId());
            }
        }
        productVersionRepository.releaseVersion(projectId, productVersionRelease.getVersionId(), productVersionRelease.getReleaseDate());
        return versionDataAssembler.doToVersionDetailDTO(productVersionMapper.selectByPrimaryKey(productVersionRelease.getVersionId()));
    }

    @Override
    public ProductVersionDetailDTO revokeReleaseVersion(Long projectId, Long versionId) {
        ProductVersionDO versionDO = new ProductVersionDO();
        versionDO.setProjectId(projectId);
        versionDO.setVersionId(versionId);
        ProductVersionE versionE = productVersionCreateAssembler.doToEntity(productVersionMapper.selectOne(versionDO));
        if (versionE == null || !Objects.equals(versionE.getStatusCode(), VERSION_STATUS_RELEASE_CODE)) {
            throw new CommonException(REVOKE_RELEASE_ERROR);
        }
        versionE.revokeReleaseVersion();
        return productVersionUpdateAssembler.entityToDto(productVersionRepository.updateVersion(versionE));
    }

    @Override
    public VersionMessageDTO queryDeleteMessageByVersionId(Long projectId, Long versionId) {
        VersionMessageDTO versionDeleteMessage = new VersionMessageDTO();
        versionDeleteMessage.setFixIssueCount(productVersionMapper.queryIssueCountByRelationType(projectId, versionId, FIX_RELATION_TYPE));
        versionDeleteMessage.setInfluenceIssueCount(productVersionMapper.queryIssueCountByRelationType(projectId, versionId, INFLUENCE_RELATION_TYPE));
        versionDeleteMessage.setVersionNames(versionStatisticsAssembler.doListToVersionNameDto(productVersionMapper.queryVersionNames(projectId, versionId)));
        return versionDeleteMessage;
    }

    @Override
    public List<ProductVersionNameDTO> queryNameByOptions(Long projectId, List<String> statusCodes) {
        return versionStatisticsAssembler.doListToVersionNameDto(productVersionMapper.queryNameByOptions(projectId, statusCodes));
    }

    @Override
    public List<ProductVersionDTO> listByProjectId(Long projectId) {
        return ConvertHelper.convertList(productVersionMapper.listByProjectId(projectId), ProductVersionDTO.class);
    }

    @Override
    public ProductVersionDetailDTO archivedVersion(Long projectId, Long versionId) {
        ProductVersionDO versionDO = new ProductVersionDO();
        versionDO.setProjectId(projectId);
        versionDO.setVersionId(versionId);
        ProductVersionE versionE = productVersionCreateAssembler.doToEntity(productVersionMapper.selectOne(versionDO));
        if (versionE == null || Objects.equals(versionE.getStatusCode(), VERSION_ARCHIVED_CODE)) {
            throw new CommonException(ARCHIVED_ERROR);
        }
        versionE.archivedVersion();
        return productVersionUpdateAssembler.entityToDto(productVersionRepository.updateVersion(versionE));
    }

    @Override
    public ProductVersionDetailDTO revokeArchivedVersion(Long projectId, Long versionId) {
        ProductVersionDO versionDO = new ProductVersionDO();
        versionDO.setProjectId(projectId);
        versionDO.setVersionId(versionId);
        ProductVersionE versionE = productVersionCreateAssembler.doToEntity(productVersionMapper.selectOne(versionDO));
        if (versionE == null || !Objects.equals(versionE.getStatusCode(), VERSION_ARCHIVED_CODE)) {
            throw new CommonException(REVOKE_ARCHIVED_ERROR);
        }
        versionE.revokeArchivedVersion();
        return productVersionUpdateAssembler.entityToDto(productVersionRepository.updateVersion(versionE));
    }

    @Override
    @Saga(code = "agile-delete-version", description = "删除版本", inputSchemaClass = VersionPayload.class)
    public Boolean mergeVersion(Long projectId, ProductVersionMergeDTO productVersionMergeDTO) {
        productVersionMergeDTO.getSourceVersionIds().remove(productVersionMergeDTO.getTargetVersionId());
        if (productVersionMergeDTO.getSourceVersionIds().isEmpty()) {
            throw new CommonException(SOURCE_VERSION_ERROR);
        }
        CustomUserDetails customUserDetails = DetailsHelper.getUserDetails();
        List<VersionIssueDO> versionIssues = productVersionMapper.queryIssueByVersionIds(projectId, productVersionMergeDTO.getSourceVersionIds(), productVersionMergeDTO.getTargetVersionId());
        versionIssueRelRepository.deleteByVersionIds(projectId, productVersionMergeDTO.getSourceVersionIds());
        if (!versionIssues.isEmpty()) {
            productVersionRepository.batchIssueToDestination(projectId, productVersionMergeDTO.getTargetVersionId(), versionIssues, new Date(), customUserDetails.getUserId());
        }
        productVersionRepository.deleteByVersionIds(projectId, productVersionMergeDTO.getSourceVersionIds());
        productVersionMergeDTO.getSourceVersionIds().forEach(versionId -> {
            VersionPayload versionPayload = new VersionPayload();
            versionPayload.setVersionId(versionId);
            versionPayload.setProjectId(projectId);
            sagaClient.startSaga("agile-delete-version", new StartInstanceDTO(JSON.toJSONString(versionPayload), "", ""));
            redisUtil.deleteRedisCache(new String[]{"Agile:VersionChart" + projectId + ':' + versionId + ":" + "*"});
        });
        return true;
    }

    @Override
    public ProductVersionDetailDTO queryVersionByVersionId(Long projectId, Long versionId) {
        ProductVersionDO productVersionDO = new ProductVersionDO();
        productVersionDO.setProjectId(projectId);
        productVersionDO.setVersionId(versionId);
        return versionDataAssembler.doToVersionDetailDTO(productVersionMapper.selectOne(productVersionDO));
    }

    @Override
    public List<Long> listIds(Long projectId) {
        return productVersionMapper.listIds();
    }

    @Override
    public synchronized ProductVersionPageDTO dragVersion(Long projectId, VersionSequenceDTO versionSequenceDTO) {
        if (versionSequenceDTO.getAfterSequence() == null && versionSequenceDTO.getBeforeSequence() == null) {
            throw new CommonException("error.dragVersion.noSequence");
        }
        ProductVersionE productVersionE = productVersionConverter.doToEntity(queryVersionByProjectIdAndVersionId(
                versionSequenceDTO.getVersionId(), projectId));
        if (productVersionE == null) {
            throw new CommonException(NOT_FOUND);
        } else {
            if (versionSequenceDTO.getAfterSequence() == null) {
                Integer maxSequence = productVersionMapper.queryMaxAfterSequence(versionSequenceDTO.getBeforeSequence(), projectId);
                versionSequenceDTO.setAfterSequence(maxSequence);
            } else if (versionSequenceDTO.getBeforeSequence() == null) {
                Integer minSequence = productVersionMapper.queryMinBeforeSequence(versionSequenceDTO.getAfterSequence(), projectId);
                versionSequenceDTO.setBeforeSequence(minSequence);
            }
            handleSequence(versionSequenceDTO, projectId, productVersionE);
        }
        return productVersionPageAssembler.doToDto(queryVersionByProjectIdAndVersionId(
                versionSequenceDTO.getVersionId(), projectId));
    }

    private void handleSequence(VersionSequenceDTO versionSequenceDTO, Long projectId, ProductVersionE productVersionE) {
        if (versionSequenceDTO.getBeforeSequence() == null) {
            productVersionE.setSequence(versionSequenceDTO.getAfterSequence() + 1);
            productVersionRepository.updateVersion(productVersionE);
        } else if (versionSequenceDTO.getAfterSequence() == null) {
            if (productVersionE.getSequence() > versionSequenceDTO.getBeforeSequence()) {
                Integer add = productVersionE.getSequence() - versionSequenceDTO.getBeforeSequence();
                if (add > 0) {
                    productVersionE.setSequence(versionSequenceDTO.getBeforeSequence() - 1);
                    productVersionRepository.updateVersion(productVersionE);
                } else {
                    productVersionRepository.batchUpdateSequence(versionSequenceDTO.getBeforeSequence(), projectId,
                            productVersionE.getSequence() - versionSequenceDTO.getBeforeSequence() + 1, productVersionE.getVersionId());
                }
            }
        } else {
            Integer sequence = versionSequenceDTO.getAfterSequence() + 1;
            productVersionE.setSequence(sequence);
            productVersionRepository.updateVersion(productVersionE);
            Integer update = sequence - versionSequenceDTO.getBeforeSequence();
            if (update >= 0) {
                productVersionRepository.batchUpdateSequence(versionSequenceDTO.getBeforeSequence(), projectId, update + 1, productVersionE.getVersionId());
            }
        }
    }


    private ProductVersionDO queryVersionByProjectIdAndVersionId(Long versionId, Long projectId) {
        ProductVersionDO productVersionDO = new ProductVersionDO();
        productVersionDO.setVersionId(versionId);
        productVersionDO.setProjectId(projectId);
        return productVersionMapper.selectOne(productVersionDO);
    }

    @Override
    public VersionIssueCountDTO queryByCategoryCode(Long projectId, Long versionId) {
        VersionIssueCountDTO result = ConvertHelper.convert(productVersionMapper.queryVersionStatisticsByVersionId(projectId, versionId), VersionIssueCountDTO.class);
        return result;
    }
}
