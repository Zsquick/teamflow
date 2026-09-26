# 参与开发

开始编码前先阅读 `docs/implementation-order.md` 和 `docs/team-collaboration.md`。

1. 从最新 `main` 创建短分支。
2. 只领取一个清晰的用户故事或技术任务。
3. 先确认 API、表结构和验收条件，再开始实现。
4. 实现对应测试，运行 `mvn verify` 和 `npm run build`。
5. 提交 PR，不在 PR 中混入无关格式化或重构。

严禁提交 `.env`、JWT 密钥、数据库密码、证书私钥、真实用户数据、上传文件和构建产物。
