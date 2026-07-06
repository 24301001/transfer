# Transfer - 前后端分离项目

一个基于 Vue 3 + Spring Boot 的前后端分离 Web 应用框架。

## 技术栈

### 前端 (Frontend)
- Vue 3
- Vite
- Vue Router
- Axios

### 后端 (Backend)
- Java 17+
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- H2 Database (开发环境)

## 项目结构

```
transfer/
├── frontend/              # 前端项目
│   ├── src/
│   │   ├── components/       # 通用组件
│   │   ├── views/            # 页面视图
│   │   ├── services/         # API 服务
│   │   ├── router/           # 路由配置
│   │   ├── App.vue
│   │   └── main.js
│   ├── public/
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── backend/               # 后端项目
│   ├── src/main/java/com/transfer/
│   │   ├── controller/       # 控制器
│   │   ├── model/            # 数据模型
│   │   ├── service/          # 业务逻辑
│   │   ├── repository/       # 数据访问
│   │   ├── config/           # 配置类
│   │   └── TransferApplication.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
└── README.md
```

## 快速开始

### 后端

```bash
cd backend
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

后端默认运行在 http://localhost:8080

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 http://localhost:5173

## API 接口

| 方法   | 路径              | 说明         |
|--------|-------------------|--------------|
| GET    | /api/health       | 健康检查     |
| GET    | /api/items        | 获取所有项目 |
| GET    | /api/items/{id}   | 获取单个项目 |
| POST   | /api/items        | 创建新项目   |
| PUT    | /api/items/{id}   | 更新项目     |
| DELETE | /api/items/{id}   | 删除项目     |

## License

MIT
