package yvon.backend.attachment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.organization.PageResponse;

import java.nio.charset.StandardCharsets;

@RestController
@Validated
@RequestMapping("/api/tasks/{taskId}/attachments")
@ConditionalOnProperty(name = "taskflow.attachment.enabled", havingValue = "true")
public class TaskAttachmentController {

    private final TaskAttachmentService attachmentService;

    public TaskAttachmentController(TaskAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('task:attachment:create')")
    public ApiResponse<TaskAttachmentResponse> upload(@PathVariable Long taskId,
                                                       @RequestPart("file") MultipartFile file,
                                                       org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(attachmentService.upload(taskId, file, principal(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:attachment:read')")
    public ApiResponse<PageResponse<TaskAttachmentResponse>> page(@PathVariable Long taskId,
                                                                   @RequestParam(defaultValue = "1") @Min(1) long page,
                                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                                                   org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(attachmentService.page(taskId, page, size, principal(authentication)));
    }

    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAuthority('task:attachment:read')")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long taskId, @PathVariable Long attachmentId,
                                                         org.springframework.security.core.Authentication authentication) {
        AttachmentDownload download = attachmentService.openDownload(taskId, attachmentId, principal(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.inputStream()));
    }

    @GetMapping("/{attachmentId}/presigned-url")
    @PreAuthorize("hasAuthority('task:attachment:read')")
    public ApiResponse<String> presignedUrl(@PathVariable Long taskId, @PathVariable Long attachmentId,
                                             org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(attachmentService.presignedUrl(taskId, attachmentId, principal(authentication)));
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('task:attachment:read')")
    public ApiResponse<Void> delete(@PathVariable Long taskId, @PathVariable Long attachmentId,
                                    org.springframework.security.core.Authentication authentication) {
        attachmentService.delete(taskId, attachmentId, principal(authentication));
        return ApiResponse.success(null);
    }

    private UserPrincipal principal(org.springframework.security.core.Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
