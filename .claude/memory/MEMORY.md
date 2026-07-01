# 项目记忆

## 规范
详见 [project-guidelines.md](project-guidelines.md):

## 技术栈
- **前端**: Vue 3 + TypeScript + Vite（入口 `src/App.vue`，`@` → `src` 别名）
- **后端**: Java 21 + Spring Boot 3.5 + Maven（包名 `com.workspace`）
- **数据库**: PostgreSQL（连接 Monsora: `jdbc:postgresql://localhost:5432/monsora`）
- **缓存**: Redis / Valkey（与 Monsora 对齐）
- **数据访问**: jOOQ DSL 为主（与 Monsora 对齐；pom 也引入了 spring-data-jpa，但仓储按 jOOQ 写）
- **迁移**: Flyway（V1~V4 已建表：spaces / space_members / nodes / audit_logs）

## 领域模型
- **Space** 工作区 → **SpaceMember** 多成员 + 权限
- **Node** 树形节点：`type`(collection/doc/image/video/audio)、`parent_id`、`sort_order`、`content`(JSON 文本)、`properties`(JSON，如图片/视频的 `src`)，软删除
- **PermissionService** 鉴权（checkCanView / checkCanEdit，抛 ForbiddenException）
- **AuditService** 审计（写入 audit_logs）

## 重要细节（jOOQ JSONB 处理）
- PostgreSQL JSONB 列用 `SQLDataType.JSONB` 插入
- jOOQ 查询返回的 JSONB 对象无法被 Jackson 序列化
- **解决**: 在 `NodeService.toMap()` 中将 JSONB 对象转成 String：
  ```java
  Object value = record.getValue(field, Object.class);
  if (value != null && value.getClass().getSimpleName().contains("JSON")) {
      value = value.toString();
  }
  ```

## API 端点
- `POST/GET/PUT/DELETE /api/spaces`
- `POST/GET/PUT/DELETE /api/space-members`
- `POST/GET /api/nodes`、`GET/PUT/DELETE /api/nodes/{id}`、`PATCH /api/nodes/{id}/move`（均需 `spaceId` 参数；nodes 软删除）
- 前端 dev 通过 Vite 中间件走 `mock/api/*.cjs` 本地 mock（生产对接后端）

## 前端现状（待补全）
- `App.vue` 是文件树侧栏 + Markdown 编辑/预览单页
- 右键菜单 action 多为空函数；上传仅 `console.log`；编辑内容尚未调用 PUT 保存
- `SPACE_ID` 暂写死为 `00000000-0000-0000-0000-000000000001`
