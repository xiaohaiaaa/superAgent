# SuperBizAgent Frontend

这是 SuperBizAgent 项目的 Web 前端界面，提供了与后端 API 交互的功能。

## 功能特性

1. **快速对话** - 使用 `/api/chat` 接口进行快速问答
2. **流式对话** - 使用 `/api/chat_stream` 接口实现流式输出
3. **AI智能运维** - 使用 `/api/ai_ops` 接口进行自动告警分析
4. **会话管理** - 支持多轮对话和历史记录管理
5. **热重载** - 开发时代码修改后自动刷新页面

## 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

**Windows用户：**
```bash
# 双击运行
start.bat

# 或命令行运行
npm run dev
```

**Mac/Linux用户：**
```bash
npm run dev
```

### 3. 访问应用

打开浏览器访问：http://localhost:3000

## 使用说明

### 快速对话功能
- 在输入框中输入问题，按回车或点击发送按钮
- 系统会自动调用后端的 `/api/chat` 接口
- 支持会话ID，留空将自动生成
- 可以清空会话历史

### 流式对话功能
- 输入问题后，AI回复会逐字显示
- 使用 `/api/chat_stream` 接口
- 支持实时查看AI思考过程

### AI智能运维功能
- 点击"开始分析"按钮
- 系统会自动执行告警分析流程
- 分析报告会实时显示输出

### 文件上传功能
- 点击侧边栏的"上传文档"按钮
- 支持拖拽上传或点击选择文件
- 支持的格式：.txt, .md
- 最大文件大小：10MB
- 上传进度条实时显示
- 文件上传后会自动创建向量索引用于查询

## 热重载说明

本项目使用 `live-server` 实现热重载功能：
- 修改任何文件（HTML、CSS、JS）后，浏览器会自动刷新
- 无需手动重启服务器
- 保持会话状态，刷新不会丢失对话内容

## 技术栈

- Bootstrap 5 - UI框架
- Bootstrap Icons - 图标库
- 原生 JavaScript - 无额外依赖
- Live Server - 开发服务器

## 注意事项

1. 确保后端服务运行在 `http://localhost:9900`
2. 如果出现跨域问题，请检查后端是否配置了CORS
3. 开发时建议使用Chrome浏览器，调试体验最佳

## 项目结构

```
frontend/
├── index.html      # 主页面
├── styles.css      # 样式文件
├── app.js          # JavaScript逻辑
├── package.json    # 项目配置
├── start.bat       # Windows启动脚本
└── README.md       # 说明文档
```

## API 说明

### 快速对话接口
```
POST http://localhost:9900/api/chat
Content-Type: application/json

{
  "Id": "session_id",
  "Question": "你的问题"
}
```

### 流式对话接口
```
POST http://localhost:9900/api/chat_stream
Content-Type: application/json

{
  "Id": "session_id", 
  "Question": "你的问题"
}
```

### AI运维接口
```
POST http://localhost:9900/api/ai_ops
```

### 文件上传接口
```
POST http://localhost:9900/api/upload
Content-Type: multipart/form-data

file: [文件内容]
```

### 清空会话接口
```
POST http://localhost:9900/api/chat/clear
Content-Type: application/json

{
  "Id": "session_id"
}
```