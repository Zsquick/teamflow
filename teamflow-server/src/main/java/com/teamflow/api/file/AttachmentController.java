package com.teamflow.api.file;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.file.dto.AttachmentResponse;
import com.teamflow.core.file.service.AttachmentDownload;
import com.teamflow.core.file.service.AttachmentService;
import com.teamflow.security.AuthenticatedUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 登录团队成员使用的附件上传、查询、下载和删除接口。 */
@RestController
@RequestMapping("/api")
public class AttachmentController {

    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final String CONTENT_TYPE_OPTIONS =
            "X-Content-Type-Options";

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = Objects.requireNonNull(
                attachmentService,
                "附件服务不能为 null"
        );
    }

    @Audited(
            action = "ATTACHMENT_UPLOAD",
            resourceType = "TASK",
            resourceId = "#taskId"
    )
    @PostMapping(
            value = "/tasks/{taskId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<AttachmentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return ApiResponse.success(
                    attachmentService.upload(
                            user.id(),
                            taskId,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            inputStream
                    )
            );
        }
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public ApiResponse<List<AttachmentResponse>> listByTask(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId
    ) {
        return ApiResponse.success(
                attachmentService.listByTask(user.id(), taskId)
        );
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<StreamingResponseBody> download(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String attachmentId
    ) {
        AttachmentDownload download = attachmentService.download(
                user.id(),
                attachmentId
        );
        String contentDisposition = ContentDisposition.attachment()
                .filename(
                        safeDownloadName(download.originalName()),
                        StandardCharsets.UTF_8
                )
                .build()
                .toString();
        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream inputStream = download.openStream()) {
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .contentType(safeMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(CONTENT_TYPE_OPTIONS, "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(responseBody);
    }

    @Audited(
            action = "ATTACHMENT_DELETE",
            resourceType = "ATTACHMENT",
            resourceId = "#attachmentId"
    )
    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String attachmentId
    ) {
        attachmentService.delete(user.id(), attachmentId);
        return ApiResponse.success(null);
    }

    private static MediaType safeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (mediaType.isWildcardType()
                    || mediaType.isWildcardSubtype()) {
                return MediaType.APPLICATION_OCTET_STREAM;
            }
            return mediaType;
        } catch (InvalidMediaTypeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String safeDownloadName(String originalName) {
        if (originalName == null) {
            return "attachment";
        }
        String normalized = originalName.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(separatorIndex + 1);
        }

        StringBuilder safeName = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> codePoint >= 0x20
                        && codePoint != 0x7F)
                .forEach(safeName::appendCodePoint);
        String result = safeName.toString().strip();
        return result.isEmpty() ? "attachment" : result;
    }
}
