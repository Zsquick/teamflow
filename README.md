# TeamFlow

TeamFlow 是一个面向求职作品集的“团队项目与任务协作平台”。它不是微服务项目，而是结构清晰的 Maven 多模块单体：规模可控，但能完整讲清认证授权、事务、并发、消息、缓存、批处理、文件 I/O、监控、测试和容器部署。


## 模块

- `teamflow-common`：统一响应、分页模型、通用错误。
- `teamflow-core`：领域实体、DTO、Mapper、业务接口与实现，不依赖 Web。
- `teamflow-server`：Spring Boot 启动、REST、安全、Redis、RabbitMQ、Batch、SSE、NIO、AOP、定时任务和监控。
- `teamflow-web`：HTML/CSS/原生 JavaScript、ES Modules、Fetch、FormData、Blob、ReadableStream 和 Vite。
- `deploy`：MySQL、Redis、RabbitMQ、Docker、Nginx 配置。
- `docs`：架构、接口、数据库、并发 I/O、协作和实现顺序。



## 当前可执行的验证

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
Set-Location teamflow-web
npm ci
npm run build
```


