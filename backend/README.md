# 道路交通事故风险预估与后果预测系统后端

Spring Boot backend for the road traffic accident risk estimation and consequence prediction system.

## 运行

```bash
mvn spring-boot:run
```

The backend starts on port `8080`.

Useful development URLs:

| URL | Description |
| --- | --- |
| `http://localhost:8080/api/v1/health` | Health check |
| `http://localhost:8080/h2-console` | H2 console |

H2 connection:

```text
JDBC URL: jdbc:h2:mem:traffic_risk_db
Username: sa
Password:
```

See `BACKEND_API.md` for endpoint details and request examples. The root `README.md` contains the full project overview, startup guide, core modules and API list.
