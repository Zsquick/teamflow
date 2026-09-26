package com.teamflow.system.controller;

import com.teamflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供不包含业务数据的系统级接口。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 检查应用的 HTTP 请求链路是否正常。
     *
     * @return 包含 pong 的统一响应
     */
    @Operation(
            summary = "检查 HTTP 服务",
            description = "公开探活接口，不需要 Bearer token，也不返回业务数据。",
            security = {}
    )
    @SecurityRequirements
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }
}
