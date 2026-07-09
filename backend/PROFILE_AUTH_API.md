# 个人中心、邮箱验证码、图形验证码接口说明

本版本新增个人中心接口、PBKDF2 密码哈希、邮箱验证码、图形验证码。

## 1. 图形验证码

`GET /api/v1/auth/captcha`

返回：

```json
{
  "captchaId": "uuid",
  "imageBase64": "data:image/png;base64,...",
  "expireSeconds": 120
}
```

前端直接将 `imageBase64` 放入 `<img src="..." />`。

## 2. 发送邮箱验证码

`POST /api/v1/auth/email-code`

用途：注册、登录、忘记密码重置。

注册：

```json
{
  "purpose": "REGISTER",
  "email": "user@example.com",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

登录：

```json
{
  "purpose": "LOGIN",
  "username": "field01",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

忘记密码：

```json
{
  "purpose": "RESET_PASSWORD",
  "username": "field01",
  "email": "user@example.com",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

开发环境默认返回 `devCode`，方便联调；生产环境请将：

```properties
app.verification.return-code-in-response=false
app.verification.console-log-code=false
```

并配置 `spring.mail.*`。

## 3. 注册

`POST /api/v1/auth/register`

```json
{
  "fullName": "张三",
  "username": "field01",
  "phone": "13800000000",
  "email": "user@example.com",
  "role": "FIELD_OFFICER",
  "password": "12345678",
  "emailCode": "123456",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

注册成功后直接返回登录 Token。

## 4. 登录

`POST /api/v1/auth/login`

```json
{
  "username": "field01",
  "password": "12345678",
  "emailCode": "123456",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

## 5. 忘记密码重置

`POST /api/v1/auth/password/reset`

```json
{
  "username": "field01",
  "email": "user@example.com",
  "newPassword": "newPassword123",
  "emailCode": "123456",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

## 6. 个人中心

所有接口需要请求头：

```http
Authorization: Bearer <token>
```

### 查看个人信息

`GET /api/v1/profile`

返回用户名、姓名、手机号、邮箱、角色、状态等信息。

### 修改姓名

`PUT /api/v1/profile/name`

```json
{
  "fullName": "李四"
}
```

### 修改密码前发送邮箱验证码

`POST /api/v1/profile/password/email-code`

```json
{
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```

### 修改密码

`PUT /api/v1/profile/password`

```json
{
  "oldPassword": "12345678",
  "newPassword": "newPassword123",
  "emailCode": "123456",
  "captchaId": "uuid",
  "captchaCode": "ABCDE"
}
```
