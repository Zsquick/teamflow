package com.teamflow.integration;

import org.testcontainers.utility.DockerImageName;

/** 集中固定集成测试所用的公共镜像仓库与内容摘要。 */
public final class TestContainerImages {

    public static final DockerImageName MYSQL = DockerImageName.parse(
            "public.ecr.aws/docker/library/mysql:8.4"
                    + "@sha256:85b9bf2e29cf836ecb8c2a15a935d4ba0c606631dff1dd79531a11983c638f2a"
    ).asCompatibleSubstituteFor("mysql");

    public static final DockerImageName REDIS = DockerImageName.parse(
            "public.ecr.aws/docker/library/redis:7.4-alpine"
                    + "@sha256:520775a41a63e77e06c73e35d2fd9cc15921a609516818796b4ecbb813078bc7"
    ).asCompatibleSubstituteFor("redis");

    public static final DockerImageName RABBITMQ = DockerImageName.parse(
            "public.ecr.aws/docker/library/rabbitmq:3.13-management-alpine"
                    + "@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281"
    ).asCompatibleSubstituteFor("rabbitmq");

    private TestContainerImages() {
    }
}
