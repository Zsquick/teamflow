package com.teamflow.api.importjob;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.importjob.dto.ImportJobResponse;
import com.teamflow.core.importjob.service.ImportJobService;
import com.teamflow.security.AuthenticatedUser;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 登录项目成员使用的 CSV 导入任务创建、启动与状态查询接口。 */
@RestController
@RequestMapping("/api/import-jobs")
public class ImportJobController {

    private final ImportJobService importJobService;

    public ImportJobController(ImportJobService importJobService) {
        this.importJobService = Objects.requireNonNull(
                importJobService,
                "导入任务服务不能为 null"
        );
    }

    @Audited(
            action = "IMPORT_JOB_CREATE",
            resourceType = "PROJECT",
            resourceId = "#projectId"
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportJobResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String projectId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return ApiResponse.success(importJobService.create(
                    user.id(),
                    projectId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    inputStream
            ));
        }
    }

    @GetMapping("/{importJobId}")
    public ApiResponse<ImportJobResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable String importJobId) {
        return ApiResponse.success(
                importJobService.get(user.id(), importJobId)
        );
    }

    @Audited(
            action = "IMPORT_JOB_START",
            resourceType = "IMPORT_JOB",
            resourceId = "#importJobId"
    )
    @PostMapping("/{importJobId}/start")
    public ApiResponse<Void> start(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable String importJobId) {
        importJobService.start(user.id(), importJobId);
        return ApiResponse.success(null);
    }
}
