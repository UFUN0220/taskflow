package yvon.backend.organization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class DepartmentService {

    private final SysDepartmentMapper departmentMapper;

    public DepartmentService(SysDepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public List<DepartmentNode> activeTree() {
        List<SysDepartmentEntity> rows = departmentMapper.selectList(
                Wrappers.<SysDepartmentEntity>lambdaQuery()
                        .eq(SysDepartmentEntity::getStatus, "ACTIVE")
                        .orderByAsc(SysDepartmentEntity::getLevel)
                        .orderByAsc(SysDepartmentEntity::getId));
        Map<Long, MutableNode> nodes = new LinkedHashMap<>();
        rows.forEach(row -> nodes.put(row.getId(), new MutableNode(row)));
        List<MutableNode> roots = new ArrayList<>();
        nodes.values().forEach(node -> {
            MutableNode parent = nodes.get(node.parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        });
        return roots.stream().map(MutableNode::toView).toList();
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (departmentMapper.selectCount(Wrappers.<SysDepartmentEntity>lambdaQuery()
                .eq(SysDepartmentEntity::getDepartmentCode, request.departmentCode())) > 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "部门编码已存在");
        }
        var duplicateNameQuery = Wrappers.<SysDepartmentEntity>lambdaQuery()
                .eq(SysDepartmentEntity::getDepartmentName, request.departmentName());
        if (request.parentId() == null) {
            duplicateNameQuery.isNull(SysDepartmentEntity::getParentId);
        } else {
            duplicateNameQuery.eq(SysDepartmentEntity::getParentId, request.parentId());
        }
        if (departmentMapper.selectCount(duplicateNameQuery) > 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "同级部门名称已存在");
        }
        SysDepartmentEntity parent = request.parentId() == null ? null : departmentMapper.selectById(request.parentId());
        if (request.parentId() != null && (parent == null || !"ACTIVE".equals(parent.getStatus()))) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "上级部门不存在或已停用");
        }
        SysDepartmentEntity entity = new SysDepartmentEntity();
        entity.setParentId(request.parentId());
        entity.setDepartmentCode(request.departmentCode());
        entity.setDepartmentName(request.departmentName());
        entity.setPath(parent == null ? "/" + request.departmentCode() + "/"
                : parent.getPath() + request.departmentCode() + "/");
        entity.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        entity.setStatus("ACTIVE");
        departmentMapper.insert(entity);
        return DepartmentResponse.from(entity);
    }

    @Transactional
    public DepartmentResponse update(Long departmentId, UpdateDepartmentRequest request) {
        SysDepartmentEntity entity = departmentMapper.selectById(departmentId);
        if (entity == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        entity.setDepartmentName(request.departmentName());
        entity.setStatus(request.status());
        entity.setVersion(request.version());
        if (departmentMapper.updateById(entity) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "部门已被其他请求修改，请刷新后重试");
        }
        return DepartmentResponse.from(departmentMapper.selectById(departmentId));
    }

    private static final class MutableNode {
        private final Long id;
        private final Long parentId;
        private final String code;
        private final String name;
        private final String path;
        private final Integer level;
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(SysDepartmentEntity row) {
            this.id = row.getId();
            this.parentId = row.getParentId();
            this.code = row.getDepartmentCode();
            this.name = row.getDepartmentName();
            this.path = row.getPath();
            this.level = row.getLevel();
        }

        private DepartmentNode toView() {
            return new DepartmentNode(id, parentId, code, name, path, level,
                    children.stream().map(MutableNode::toView).toList());
        }
    }
}
