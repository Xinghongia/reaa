# Vue3 前端

## 概述

Vue3 前端是 Web 模块的用户界面层，提供书架管理、书源编辑、RSS 订阅等功能。采用**组件化**架构，使用 Vue 3 + Pinia + Vue Router 构建。

**学习目标**：理解前端架构设计，掌握组件通信和状态管理原理。

---

## 架构设计

### 为什么要用 Hash 路由？

```typescript
const router = createRouter({
  history: createWebHashHistory(),  // 使用 Hash 模式
  routes: [...]
})
```

**设计原理**：

- **History 模式**需要服务器配置，URL 类似 `/book/shelf`
- **Hash 模式**使用 `#` 分隔，URL 类似 `/book/shelf#/`
- 优点：无需服务器配置，直接 `file://` 访问也能工作

**场景**：用户在浏览器输入 `file:///path/to/index.html#/book` 可以直接访问书架页。

### 为什么用 Pinia 而非 Vuex？

```typescript
export const useBookStore = defineStore('book', {
  state: () => ({ shelf: [], ... }),
  actions: {
    async loadBookShelf() { ... }
  }
})
```

**设计原理**：

- **Pinia** 是 Vue 官方推荐的新一代状态管理
- **更简单的 API**：不需要 mutations，只有 state、getters、actions
- **TypeScript 支持更好**：类型推断更准确
- **轻量**：约 1KB 的体积

**为什么集中管理状态？**：

- 书架数据被多个组件共用（BookShelf、BookChapter）
- 避免 Prop Drilling（层层传递）
- DevTools 可追踪所有状态变更

---

## 组件化设计

### 组件职责划分

```
BookShelf.vue (页面组件)
├── BookItems.vue (书籍列表)
│   └── 展示书架上的书籍
├── SearchBar (搜索框)
│   └── 触发搜索
└── BookItems → BookChapter.vue (跳转)
    └── 阅读章节
```

**设计原则**：

- **页面组件**：路由入口，处理业务逻辑
- **展示组件**：只负责 UI 渲染
- **复用组件**：SourceTabForm、ToolBar 等可跨页面使用

### 为什么用 Composition API？

```typescript
<script setup lang="ts">
const isNight = computed(() => store.isNight)
const searchWord = ref('')

const searchBook = async () => {
  await store.searchBooks(searchWord.value)
}
</script>
```

**优势**：

- **逻辑复用**：Composables 可以抽离为独立函数
- **类型推断**：TypeScript 支持更好
- **更少的样板代码**：不需要 `export default`
- **响应式透明**：`ref` 和 `reactive` 自动追踪依赖

---

## 状态管理原理

### Pinia Store 结构

```typescript
// modules/web/src/store/bookStore.ts
export const useBookStore = defineStore('book', {
  // 1. State - 状态仓库
  state: () => ({
    shelf: [] as Book[],
    readingBook: null,
  }),

  // 2. Getters - 计算属性
  getters: {
    bookProgress: (state): BookProgress | undefined => {
      return state.readingBook?.durChapterIndex
    }
  },

  // 3. Actions - 业务逻辑
  actions: {
    async loadBookShelf() {
      const data = await API.getBookShelf()
      this.shelf = data
    }
  }
})
```

**设计模式 - Store 模式**：

- 单一数据源：所有状态集中管理
- 单向数据流：State → View → Action → State
- 可预测性：状态变更都可追踪

### Store 初始化

```typescript
// modules/web/src/main.ts
import { useBookStore } from '@/store/bookStore'

// 在根组件中初始化
const store = useBookStore()
store.loadBookShelf()  // 页面加载时获取书架
```

---

## 路由设计

### 路由懒加载

```typescript
// modules/web/src/router/bookRouter.ts
export const bookRoutes = {
  path: '/book',
  children: [
    {
      path: '',
      name: 'shelf',
      component: () => import('@/views/BookShelf.vue'),  // 懒加载
    }
  ]
}
```

**为什么懒加载？**

- 首屏加载更快：不需要一次性加载所有页面
- 减少内存占用：不访问的页面不加载
- 按需获取：用户点击时才加载

**原理**：Webpack 会将懒加载的组件打包成独立的 chunk 文件，访问时再请求。

---

## 学习任务

### 1. 理解前端架构

**打开文件**：

- `modules/web/src/main.ts` - 入口文件
- `modules/web/src/App.vue` - 根组件
- `modules/web/src/router/index.ts` - 路由配置

**思考**：

- 为什么 App.vue 只有一个 `<router-view />`？
- Hash 路由和 History 路由的区别是什么？

### 2. 探索状态管理

**打开文件**：

- `modules/web/src/store/bookStore.ts` - 书架状态
- `modules/web/src/store/sourceStore.ts` - 书源状态

**思考**：

- Store 的 state、getters、actions 有什么区别？
- 为什么要用 `markRaw()` 处理书源数据？

### 3. 分析组件通信

**打开文件**：

- `modules/web/src/views/BookShelf.vue` - 书架页面
- `modules/web/src/components/BookItems.vue` - 书籍列表

**思考**：

- BookShelf 如何把数据传给 BookItems？
- 如果不用 Store，数据如何传递？

---

## 设计亮点总结

| 设计 | 原理 | 收益 |
|------|------|------|
| Hash 路由 | # 分隔无需服务器配置 | 兼容性强 |
| Pinia Store | 单一数据源 + 单向数据流 | 可追踪、可预测 |
| 懒加载 | Webpack chunk 按需加载 | 首屏快、内存省 |
| Composition API | 逻辑抽离为 Composables | 可复用、易测试 |
| Props/Emit | 父子组件通信 | 职责分离 |
