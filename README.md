# TeamFlow

TeamFlow 是一个面向求职作品集的“团队项目与任务协作平台”。它不是微服务项目，而是结构清晰的 Maven 多模块单体：规模可控，但能完整讲清认证授权、事务、并发、消息、缓存、批处理、文件 I/O、监控、测试和容器部署。

当前仓库采用“Codex 生成代码和测试，用户逐类逐方法学习”的协作方式。F01-F17 的代码均已完成；F01-F10 已完成讲解，下一次讲解从 F11 RabbitMQ 通知与 SSE 开始，并依次讲到 F17。

## 模块

- `teamflow-common`：统一响应、分页模型、通用错误。
- `teamflow-core`：领域实体、DTO、Mapper、业务接口与实现，不依赖 Web。
- `teamflow-server`：Spring Boot 启动、REST、安全、Redis、RabbitMQ、Batch、SSE、NIO、AOP、定时任务和监控。
- `teamflow-web`：HTML/CSS/原生 JavaScript、ES Modules、Fetch、FormData、Blob、ReadableStream 和 Vite。
- `deploy`：MySQL、Redis、RabbitMQ、Docker、Nginx 配置。
- `docs`：架构、接口、数据库、并发 I/O、协作和实现顺序。

## 推荐阅读顺序

1. [当前进度与续接记录](docs/CURRENT-PROGRESS.md)：确认现在做到哪里、下一步是什么。
2. [逐功能、逐类、逐方法手册](docs/code-guide/README.md)：确认现行 F01-F17 编号与协作规则。
3. [项目概括](docs/project-overview.md)
4. [架构与技术映射](docs/architecture.md)
5. [数据库现行约定](docs/database-schema.md)
6. [REST API 清单](docs/api-plan.md)
7. [多线程与 I/O 专题](docs/concurrency-and-io.md)
8. [团队协作约定](docs/team-collaboration.md)
9. [本地与 Docker 启动](docs/setup-and-deployment.md)

## 当前可执行的验证

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
Set-Location teamflow-web
npm ci
npm run build
```

最近一次完整验证结果以 [`docs/CURRENT-PROGRESS.md`](docs/CURRENT-PROGRESS.md) 为准。每个功能代码生成后都要运行与风险相称的测试，但只有用户发出“讲解”指令后才进入详细讲解，只有讲解完成并收到“代码”指令后才进入下一功能。
