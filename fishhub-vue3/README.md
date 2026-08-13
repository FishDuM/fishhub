# FishHub Web

FishHub 前端使用 Vue 3、Vite、Pinia 和 Tailwind CSS。

## 开发

需要 Node.js 18 或更高版本。安装依赖并启动开发服务器：

```bash
npm install
npm run dev
```

开发环境中的 `/api` 请求会由 Vite 代理到 `http://localhost:8000`，因此需要先启动 FishHub Gateway。浏览器登录状态由 Pinia 持久化保存，不使用 Cookie 工具模块。

## 构建

```bash
npm run build
```

构建结果位于 `dist/`。生产环境应由 Nginx 等 Web 服务器托管静态文件，并将 `/api` 反向代理到 Gateway；不要把业务服务端口直接暴露给浏览器。

本地预览生产构建：

```bash
npm run preview
```
