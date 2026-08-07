package yvon.backend.attachment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.organization.PageResponse;

@Service
@ConditionalOnProperty(name = "taskflow.attachment.enabled", havingValue = "true")
public class TaskAttachmentMetadataService {

    private final TaskAttachmentMapper mapper;

    public TaskAttachmentMetadataService(TaskAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public TaskAttachmentEntity createPending(TaskAttachmentEntity attachment) {
        attachment.setStatus("UPLOADING");
        attachment.setDeleted(0);
        mapper.insert(attachment);
        return attachment;
    }

    @Transactional
    public TaskAttachmentEntity markAvailable(TaskAttachmentEntity attachment, Long operatorId) {
        requireUpdate(attachment, "UPLOADING", "AVAILABLE", 0, 0, operatorId);
        attachment.setStatus("AVAILABLE");
        attachment.setVersion(attachment.getVersion() + 1);
        return attachment;
    }

    @Transactional
    public void markFailed(TaskAttachmentEntity attachment, Long operatorId) {
        if (mapper.updateStatusWithVersion(attachment.getId(), "UPLOADING", "FAILED", 0, 0,
                attachment.getVersion(), operatorId) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "附件状态已变化，无法标记上传失败");
        }
    }

    @Transactional
    public TaskAttachmentEntity markDeleted(TaskAttachmentEntity attachment, Long operatorId) {
        requireUpdate(attachment, "AVAILABLE", "DELETED", 0, 1, operatorId);
        attachment.setStatus("DELETED");
        attachment.setDeleted(1);
        attachment.setVersion(attachment.getVersion() + 1);
        return attachment;
    }

    @Transactional
    public void markCleanupFailed(TaskAttachmentEntity attachment, Long operatorId) {
        if (mapper.updateStatusWithVersion(attachment.getId(), "DELETED", "FAILED", 1, 0,
                attachment.getVersion(), operatorId) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "附件清理状态已变化");
        }
    }

    public TaskAttachmentEntity requireAvailable(Long attachmentId) {
        TaskAttachmentEntity attachment = mapper.selectById(attachmentId);
        if (attachment == null || !"AVAILABLE".equals(attachment.getStatus())) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "附件不存在或不可用");
        }
        return attachment;
    }

    public PageResponse<TaskAttachmentEntity> page(Long taskId, long page, long size) {
        Page<TaskAttachmentEntity> result = mapper.selectPage(new Page<>(page, size),
                Wrappers.<TaskAttachmentEntity>lambdaQuery()
                        .eq(TaskAttachmentEntity::getTaskId, taskId)
                        .eq(TaskAttachmentEntity::getStatus, "AVAILABLE")
                        .orderByDesc(TaskAttachmentEntity::getCreatedAt));
        return new PageResponse<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    private void requireUpdate(TaskAttachmentEntity attachment, String fromStatus, String toStatus,
                               int fromDeleted, int toDeleted, Long operatorId) {
        if (mapper.updateStatusWithVersion(attachment.getId(), fromStatus, toStatus, fromDeleted, toDeleted,
                attachment.getVersion(), operatorId) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "附件状态已变化，请刷新后重试");
        }
    }
}
