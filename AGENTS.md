# Security Baseline (MANDATORY)

## 1. 禁止动态代码执行
- 禁止使用 DexClassLoader
- 禁止反射执行远程内容
- 禁止加载或执行网络下发脚本

## 2. 更新机制限制
- 仅允许下载静态数据（JSON / 图片）
- 必须进行签名校验（Ed25519 / RSA）
- 必须进行版本白名单校验
- 必须进行 hash 校验（hash pinning）

## 3. 网络策略
- 默认禁止出站网络请求
- 所有网络行为必须用户主动触发
- 必须使用 HTTPS
- 必须进行证书锁定（certificate pinning）

## 4. 审计日志
- 所有更新行为必须记录日志
- 包括请求、校验、加载结果
- 日志必须持久化
- 不允许被普通业务逻辑修改

## 5. 不可信输入原则
- 所有外部数据默认不可信
- 必须校验后才能使用

# Domain Architecture (MANDATORY)

## config/
- ConfigRepository
- RuntimeStateStore

## safety/
- UpdatePolicy
- IntegrityVerifier
- AuditLogger

## compat/
- RomQuirkAdapter
- PermissionFlowCoordinator

## image/
- RegionScanner
- PatternMatcher
- TemplateMatcher
