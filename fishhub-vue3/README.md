# 🎨 FishHub Web（前端应用）

> 基于 **Vue 3 (Composition API / script setup) + Vite 5 + Pinia + Tailwind CSS** 构建的现代化社区 SPA 前端，完美复刻小红书与飞鱼社区的流畅交互体验。

---

## ✨ 核心特性与功能模块

1. **响应式瀑布流与频道切换**：
   - 首页瀑布流（Waterfall）卡片式布局，支持图文笔记与视频卡片自适应渲染；
   - 顶部多频道滑动切换（全部、穿搭、美食、旅行、数码等），基于 Vue Router 响应式驱动数据平滑刷新。
2. **沉浸式笔记详情弹窗 & 多级嵌套评论**：
   - 左右分栏沉浸式详情弹窗（左侧多图/视频轮播，右侧作者信息与评论互动）；
   - 一级评论与二级嵌套回复树状展示，支持按热度权重排序与实时点赞动画。
3. **小红书风格富文本发布器 (`PublishModal.vue`)**：
   - 支持多图/视频上传与封面裁切预览；
   - 标题、正文、标签（Topic）、分类频道选择与权限设置（公开/私密）。
4. **统一认证与安全防刷弹窗 (`LoginModal.vue`)**：
   - 手机号 + 密码注册/登录无缝切换；
   - 对接后端图形验证码防刷防护（5 分钟有效，输错 10 次原子作废，未满 10 次保留原图方便重试）。
5. **个人主页 (`Profile.vue`)**：
   - 个人资料展示（获赞与收藏数、关注数、粉丝数）；
   - 动态 Tab 切换（已发布笔记、收藏列表、点赞历史）。

---

## 🛠️ 技术栈清单

- **框架**：Vue 3.4+ (`<script setup>`)
- **构建工具**：Vite 5
- **状态管理**：Pinia（持久化存储 Token 与用户信息）
- **路由**：Vue Router 4
- **样式方案**：Tailwind CSS + Lucide Icons
- **网络请求**：Axios（统一拦截器、自动携带 Bearer Token、全局错误消息提醒）
- **动画动效**：GSAP

---

## 📁 目录结构

```
fishhub-vue3
├── src
│   ├── api/             # API 接口请求定义（auth、note、comment、user、relation、search）
│   ├── assets/          # 静态资源与样式
│   ├── components/      # 通用业务组件
│   │   ├── auth/        # 登录/注册 Modal 与条款确认
│   │   ├── comment/     # 评论列表与输入框
│   │   ├── common/      # 全局消息 Toast、Loading
│   │   └── note/        # 笔记卡片 NoteCard、详情 Modal、发布器 PublishModal
│   ├── router/          # Vue Router 路由配置
│   ├── stores/          # Pinia 状态中心（user.js 等）
│   ├── utils/           # 工具函数（message 消息提示、时间格式化等）
│   ├── views/           # 页面视图（Discover 发现页、Profile 个人中心等）
│   ├── App.vue          # 根组件
│   └── main.js          # 入口文件
├── vite.config.js       # Vite 配置文件（开发代理 /api -> localhost:8000）
└── package.json
```

---

## 🚀 本地开发与构建

### 1. 安装依赖
```bash
npm install
```

### 2. 启动开发服务器
```bash
npm run dev
```
> **提示**：开发环境中所有 `/api/*` 请求会被 Vite 自动反向代理至后端的 `http://localhost:8000`（FishHub Gateway 网关服务），请确保已提前启动网关服务。

### 3. 生产环境构建
```bash
npm run build
```
构建产物输出至 `dist/` 目录，可由 Nginx 托管静态资源（详见根目录 `deploy/README.md`）。

### 4. 预览生产构建
```bash
npm run preview
```

