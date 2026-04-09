# 项目复现指南

本文档说明如何从零开始完整复现本项目，包括 Dify 工作流导入、环境变量配置、Docker 启动和基础功能验证。

## 一、准备条件

复现前请先准备：

- JDK 17
- Node.js 18+
- npm 9+
- Maven 3.9+
- Docker Desktop
- 一个可用的 Dify 平台账号
- 一个发布后的 Dify App API Key

## 二、导入 Dify 工作流

工作流文件位于：

- [原始版本](./workflows/电商全能客服.yml)
- [流式优化版](./workflows/电商全能客服-流式优化版.yml)

推荐使用流式优化版。

### 导入步骤

1. 打开 Dify 控制台
2. 新建或进入一个 Chatflow 应用
3. 导入 `电商全能客服-流式优化版.yml`
4. 检查以下节点配置是否已在当前环境中正确绑定：
   - 模型节点
   - 知识检索节点
   - Code 节点
5. 发布应用
6. 复制新的 App API Key

## 三、配置项目环境变量

进入项目根目录后，复制模板：

```powershell
Copy-Item .env.example .env
```

然后填写 `.env`：

```env
DIFY_API_KEY=app-your-dify-app-key
MYSQL_PASSWORD=your_mysql_password
BACKEND_HOST_PORT=8400
DIFY_API_BASE_URL=https://api.dify.ai/v1
APP_CORS_ALLOWED_ORIGINS=http://localhost,http://127.0.0.1,http://localhost:5173,http://127.0.0.1:5173
```

## 四、启动方式

### 方式一：Docker Compose 启动

推荐使用此方式，最省事、最接近展示环境。

```powershell
docker compose up -d --build
```

启动后：

- 前端地址：`http://localhost`
- 后端健康检查：`http://localhost:8400/api/chat/health`

### 方式二：本地开发启动

#### 启动后端

```powershell
cd backend
mvn spring-boot:run
```

#### 启动前端

```powershell
cd frontend
npm install
npm run dev
```

## 五、验证步骤

### 1. 健康检查

访问：

- `http://localhost:8400/api/chat/health`

预期返回：

```json
{"service":"customer-service","status":"ok"}
```

### 2. 首页访问

访问：

- `http://localhost`

预期结果：

- 能看到聊天页面
- 能输入消息
- 前端无明显白屏或报错

### 3. 流式输出验证

建议测试：

- `你好`
- `帮我查一下订单1001现在到哪了`
- `机器显示 E01 是什么意思？`

预期结果：

- 发送后前端先出现状态提示
- 随后 assistant 回复逐步显示
- 订单查询场景下会先看到“正在为您查询订单，请稍候...”

### 4. 历史记录验证

验证接口：

- `GET /api/chat/conversations`
- `GET /api/chat/history/{conversationId}`

预期结果：

- 可以查到新建会话
- 可以查到 user 和 assistant 两类消息

## 六、建议的答辩或作品集演示顺序

如果你要用于课程设计、答辩或作品展示，可以按以下顺序演示：

1. 打开 README 展示系统概览与架构图
2. 展示 Dify Chatflow 工作流图
3. 启动项目并打开聊天界面
4. 演示售前问答或故障问答
5. 演示订单查询分支
6. 演示流式输出过程
7. 展示数据库中的会话和消息记录

## 七、常见问题

### 1. 页面能打开，但发送消息没有回复

优先检查：

- `.env` 中 `DIFY_API_KEY` 是否填写正确
- Dify 工作流是否已发布
- 当前 Key 是否对应正确的应用

### 2. 看起来还是一次性输出

优先检查：

- Dify 工作流是否仍然使用末端变量聚合统一回复
- 是否使用了流式优化版工作流
- 前端是否走的是当前仓库中的 SSE 解析逻辑

### 3. Code 节点执行失败

通常与 Dify Sandbox 服务有关，需要检查：

- Dify Sandbox 是否启动
- 证书和反代配置是否正常
- 自建环境是否存在域名不匹配问题

## 八、复现完成的标志

当你看到以下现象，就说明复现基本成功：

- 前端和后端都可访问
- 用户消息可以进入 Dify 工作流
- assistant 回复会实时显示在页面中
- 数据库中能查到会话和消息记录
- 订单查询、知识问答和闲聊至少有一个场景能跑通
