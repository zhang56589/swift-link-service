# Swift Link Service - 分布式短链接跳转服务

## 1. 项目简介

Swift Link Service 是一个面向营销短信、推广页面、站内活动等场景的分布式短链接服务，基于 Spring Boot 3.2 + Java 17 构建。核心能力包括：

- 长链接压缩为 Base62 短码，对外隐藏自增 ID 与业务量
- 短链接 302 重定向，支持过期失效、停用、域名安全校验
- 访问明细异步记录，PV/UV 实时统计与历史数据落库
- Redis 缓存、短码预生成池、Redisson 分布式锁、Lua 分布式限流
- Nacos 服务注册与配置中心，支持多实例集群部署

项目不依赖 Guava，所有工具能力均使用 JDK 原生 API 或项目内实现。

## 2. 业务场景

| 场景 | 说明 |
| --- | --- |
| 营销短信 | 短信长度有限，将长活动链接压缩为 10 位左右短链，降低短信成本 |
| 推广页面 | 不同渠道生成不同业务标识的短链，按渠道统计访问量 |
| 站内活动 | 活动结束后短链自动过期，避免无效流量继续跳转 |
| 安全风控 | IP/域名黑名单、内网地址拦截、接口限流，防止恶意刷量与开放重定向滥用 |

典型链路：

```
用户点击短链 -> 302 跳转服务 -> 校验限流/黑名单/过期 -> 读取缓存或回源 MySQL
            -> 异步记录访问日志 -> 302 Location 跳转原始长链接
```

## 3. 核心模块

### 3.1 短码生成模块

1. 雪花算法生成全局唯一 ID：41bit 时间戳 + 5bit 机房 + 5bit 机器 + 12bit 序列号。
2. Base62 编码为短码，当前 ID 规模下约 10 位，不暴露自增规律。
3. Redis List 预生成短码池：应用启动预热、定时补充、创建时 `LPOP` 取码，把生成成本从请求链路前置。
4. Redisson 分布式锁保护短码池补充与短码落库，MySQL 唯一索引 `uk_short_code` 作为最终兜底。

### 3.2 跳转模块

1. 路由 `GET /{shortCode}` 返回 HTTP 302，使用 302 而非 301，保证链接状态、目标地址可调整。
2. 访问顺序：IP 限流 -> IP 黑名单 -> 短码限流 -> 缓存查询 -> 缓存重建锁/回源 -> 状态与过期校验 -> 域名安全校验 -> 异步统计。
3. 缓存使用 Cache-Aside 模式，缓存值包含原始链接、状态、绝对过期时间戳。
4. 缓存 TTL 与链接过期时间取较小值，并叠加随机抖动，避免缓存雪崩。
5. 不存在的短码写入 30 秒空值缓存，避免缓存穿透。

### 3.3 访问统计模块

1. 跳转成功后投递异步任务，线程池 `access-log-executor` 负责落库与计数。
2. 访问明细写入 `t_access_log`，包含 IP、UA、Referer、访问时间。
3. Redis Hash 累加 PV，HyperLogLog 估算 UV，内存占用固定。
4. 定时任务每 5 分钟将活跃统计 key 写入 `t_link_daily_stats`，使用 `GREATEST` 幂等更新，重复执行不会造成数据回退。
5. 查询当日数据读 Redis，查询历史数据读 MySQL，并返回累计 PV/UV。

### 3.4 安全与限流模块

1. Redis + Lua 固定窗口限流，脚本内完成 `INCR + PEXPIRE`，保证原子性。
2. 双层限流：IP 维度 60 次/分钟，短码维度 1000 次/分钟，均可通过配置调整。
3. IP 黑名单、域名黑名单存放在 Redis Set，多节点即时生效。
4. 可选域名白名单（`swift-link.allow-hosts`），未配置时仅受黑名单约束。
5. 拦截回环、私有网段、链路本地地址，避免短链服务被用作内网探测入口。
6. 管理接口令牌使用常量时间比较，降低时序攻击风险。

## 4. 技术栈

| 组件 | 版本/说明 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.5 |
| Spring Cloud Alibaba | 2023.0.3.2 |
| Nacos | 服务注册与配置中心（可选启用） |
| MySQL | 8.x，存储短链、访问明细、天级统计 |
| Redis | 7.x，缓存、短码池、限流、统计、黑名单 |
| Redisson | 3.27.2，分布式锁 |
| MyBatis-Plus | 3.5.7 |
| Maven | 3.9+ |
| Lombok | 精简样板代码 |

## 5. 架构说明

### 5.1 部署架构

```
                       +----------------------+
                       |   Nginx / API 网关    |
                       |  统一入口、TLS、转发  |
                       +----------+-----------+
                                  |
             +--------------------+--------------------+
             |                    |                    |
     +-------v------+     +-------v------+     +-------v------+
     | 实例 A       |     | 实例 B       |     | 实例 C       |
     | Spring Boot  |     | Spring Boot  |     | Spring Boot  |
     +---+------+---+     +---+------+---+     +---+------+---+
         |      |             |      |             |      |
         |      +-------------+------+-------------+      |
         |                    |                           |
  +------v------+      +------v------+             +------v------+
  |   MySQL     |      |    Redis    |             |    Nacos    |
  | 短链/日志/  |      | 缓存/池/锁/ |             | 注册/配置   |
  | 统计持久化  |      | 限流/统计   |             |             |
  +-------------+      +-------------+             +-------------+
```

### 5.2 分层结构

```
controller  接收 HTTP 请求、参数校验、返回统一 Result
service     业务编排、缓存/锁/限流/统计逻辑
mapper      MyBatis-Plus 数据访问
entity      数据库实体，与表字段一一对应
dto         请求/响应/缓存/异步消息对象
config      Redis、Redisson、线程池、业务配置
util        雪花算法、Base62、URL/IP 工具、Redis Key 规范
exception   错误码、业务异常、全局异常处理
```

### 5.3 跳转时序

```
Client          Controller        RedirectService      Redis           MySQL
  |                  |                    |               |               |
  |-- GET /{code} -->|                    |               |               |
  |                  |-- resolve() ------>|               |               |
  |                  |                    |-- 限流 Lua -->|               |
  |                  |                    |-- 黑名单 ----->|               |
  |                  |                    |-- 查缓存 ---->|               |
  |                  |                    |<-- miss ------|               |
  |                  |                    |-- 重建锁 ---->|               |
  |                  |                    |-- 回源 ---------------------->|
  |                  |                    |<-- 短链数据 ------------------|
  |                  |                    |-- 写缓存 ---->|               |
  |                  |                    |-- 异步统计 ------------------>|
  |<-- 302 Location -|                    |               |               |
```

### 5.4 Redis Key 设计

| Key | 类型 | 用途 | TTL |
| --- | --- | --- | --- |
| `short:code:pool` | List | 预生成短码池 | 无 |
| `short:link:{code}` | String | 短链接缓存（JSON） | 30 分钟 + 随机抖动，且不超过链接过期时间 |
| `short:link:{code}` = `__NULL__` | String | 空值缓存，防穿透 | 30 秒 |
| `lock:short:pool:replenish` | RLock | 短码池补充锁 | 10 秒 |
| `lock:short:code:{code}` | RLock | 短码落库锁 | 3 秒 |
| `lock:cache:rebuild:{code}` | RLock | 热点缓存重建锁 | 1 秒 |
| `rl:ip:{ip}:{window}` | String | IP 固定窗口计数 | 窗口时长 |
| `rl:code:{code}:{window}` | String | 短码固定窗口计数 | 窗口时长 |
| `short:blacklist:ip` | Set | IP 黑名单 | 无 |
| `short:blacklist:host` | Set | 域名黑名单 | 无 |
| `short:stats:pv:{code}:{yyyyMMdd}` | Hash | 当日 PV | 3 天 |
| `short:stats:uv:{code}:{yyyyMMdd}` | HyperLogLog | 当日 UV | 3 天 |
| `short:stats:active` | Set | 待落库的统计 key | 无 |

## 6. 项目目录结构

```
swift-link-service
├── pom.xml
├── README.md
├── sql
│   └── schema.sql
└── src
    ├── main
    │   ├── java/com/swiftlink
    │   │   ├── SwiftLinkApplication.java
    │   │   ├── config
    │   │   │   ├── AsyncConfig.java
    │   │   │   ├── RedissonConfig.java
    │   │   │   ├── RedisConfig.java
    │   │   │   └── SwiftLinkProperties.java
    │   │   ├── controller
    │   │   │   ├── BlacklistController.java
    │   │   │   ├── LinkController.java
    │   │   │   ├── RedirectController.java
    │   │   │   └── StatsController.java
    │   │   ├── dto
    │   │   │   ├── AccessLogMessage.java
    │   │   │   ├── BlacklistRequest.java
    │   │   │   ├── CreateLinkRequest.java
    │   │   │   ├── CreateLinkResponse.java
    │   │   │   ├── LinkCacheDTO.java
    │   │   │   ├── LinkDetailVO.java
    │   │   │   ├── LinkStatsVO.java
    │   │   │   └── Result.java
    │   │   ├── entity
    │   │   │   ├── AccessLog.java
    │   │   │   ├── LinkDailyStats.java
    │   │   │   └── ShortLink.java
    │   │   ├── exception
    │   │   │   ├── BusinessException.java
    │   │   │   ├── ErrorCode.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── RateLimitException.java
    │   │   ├── mapper
    │   │   │   ├── AccessLogMapper.java
    │   │   │   ├── LinkDailyStatsMapper.java
    │   │   │   └── ShortLinkMapper.java
    │   │   ├── service
    │   │   │   ├── AccessLogService.java
    │   │   │   ├── BlacklistService.java
    │   │   │   ├── LinkCacheService.java
    │   │   │   ├── RateLimitService.java
    │   │   │   ├── RedirectService.java
    │   │   │   ├── ShortCodeService.java
    │   │   │   ├── ShortLinkService.java
    │   │   │   └── StatsService.java
    │   │   └── util
    │   │       ├── Base62Util.java
    │   │       ├── IpUtil.java
    │   │       ├── LinkStatus.java
    │   │       ├── RedisKeyUtil.java
    │   │       ├── SnowflakeIdGenerator.java
    │   │       └── UrlUtil.java
    │   └── resources
    │       ├── application.yml
    │       └── lua
    │           └── rate_limit.lua
    └── test/java/com/swiftlink
        ├── service
        │   └── ShortLinkServiceTest.java
        └── util
            ├── Base62UtilTest.java
            ├── SnowflakeIdGeneratorTest.java
            └── UrlUtilTest.java
```

## 7. 接口说明

统一响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 7.1 创建短链接

```http
POST /api/links
Content-Type: application/json

{
  "originUrl": "https://example.com/activity/detail?id=123",
  "expireTime": "2026-12-31 23:59:59",
  "businessDomain": "sms-activity"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "shortCode": "3Kx9aB2c",
    "shortUrl": "http://s.link/3Kx9aB2c",
    "expireTime": "2026-12-31 23:59:59",
    "createTime": "2026-10-21 14:30:00"
  }
}
```

### 7.2 跳转

```http
GET /{shortCode}
```

成功返回 `302`，响应头包含 `Location`；不存在返回 `404`，停用返回 `403`，过期返回 `410`，限流返回 `429` 且带 `Retry-After`。

```bash
curl -i http://127.0.0.1:8080/3Kx9aB2c
```

### 7.3 查询详情与状态变更

```http
GET /api/links/{shortCode}
PUT /api/links/{shortCode}/status?status=0
```

`status`：`0-停用`，`1-启用`。状态变更后立即删除对应缓存。

### 7.4 访问统计

```http
GET /api/stats/{shortCode}
GET /api/stats/{shortCode}?date=2026-10-20
```

不传 `date` 查询当日实时数据，传 `date` 查询历史数据；响应同时包含累计 PV/UV。

### 7.5 黑名单管理

```http
POST   /api/admin/blacklist/ip        Header: X-Admin-Token
DELETE /api/admin/blacklist/ip?value=1.2.3.4
POST   /api/admin/blacklist/host
DELETE /api/admin/blacklist/host?value=evil.com
```

请求体：`{"value": "1.2.3.4"}` 或 `{"value": "evil.com"}`。

## 8. 核心难点

### 8.1 短码全局唯一

- 雪花算法在集群内通过 `workerId + datacenterId` 保证不重复，单机每毫秒 4096 个序列。
- 短码落库前使用 `lock:short:code:{code}` 分布式锁串行化“查重 + 插入”，降低并发冲突。
- 数据库 `uk_short_code` 唯一索引作为最终一致性兜底，冲突时自动换码重试，最多 3 次。
- 等待下一毫秒限制最大 1000 次重试且不超过 500ms，循环内 `Thread.yield()` 让出 CPU；超时抛出异常，防止系统时钟停滞时 while 空转耗尽 CPU。
- 发号并发控制使用 `ReentrantLock` 限时锁（200ms）替代 `synchronized`，超时或中断快速失败，避免时钟异常时线程无限阻塞堆积。
- 短码池补充使用独立锁 `lock:short:pool:replenish`，避免集群多节点同时批量生成。

### 8.2 预生成池的吞吐与兜底

- 正常请求只执行一次 `LPOP`，不访问数据库生成 ID。
- 池容量低于阈值时在锁内二次检查当前容量，防止重复补充。
- 池为空且补充失败时直接生成短码兜底，保证创建接口可用性。
- 定时任务每 30 秒检查一次池容量，避免流量低谷后池长期不足。
- 创建时未抢到落库锁或短码查重命中，通过 `RPUSH` 将短码归还池尾，避免并发下短码被白白丢弃；归还失败仅记日志丢弃，不阻塞创建链路。

### 8.3 缓存穿透、击穿、雪崩

- 穿透：不存在的短码写入 `__NULL__` 空值缓存，TTL 30 秒；反序列化失败或结果为空的脏缓存同样改写为空值缓存，防止脏数据被反复写入后持续击穿数据库。
- 击穿：热点 key 失效时使用 `lock:cache:rebuild:{code}` 重建，未抢到锁的线程短暂自旋读取缓存；自旋 60ms 仍未命中说明重建异常或极慢，直接抛出 `SYSTEM_BUSY` 降级回源请求，数据库异常时同样抛出 `SYSTEM_BUSY`，防止高并发下请求继续压向数据库。
- 雪崩：缓存 TTL 增加 0~300 秒随机抖动；缓存 TTL 不超过链接过期时间，避免过期链接长期驻留。
- 一致性：停用/启用后主动删除缓存，其他节点下次访问回源加载新状态。

### 8.4 分布式限流

- Lua 脚本内原子执行 `INCR` 与 `PEXPIRE`，避免“先加计数后设过期”导致的死 key。
- 窗口序号拼入 key，窗口切换天然完成，无需额外清理。
- 限流组件异常时降级放行并记录 `error` 日志，优先保证跳转可用性；黑名单查询异常时返回系统繁忙，避免风控失效时误放行。
- 限流拦截时打印携带 IP/短码与 `Retry-After` 的 `warn` 日志，便于监控识别防刷对象。
- 限流返回 `Retry-After`，便于客户端退避。

### 8.5 异步统计与幂等落库

- 跳转线程只投递异步任务，日志落库和 Redis 计数不阻塞 302 响应。
- 线程池队列满时使用 `CallerRunsPolicy`，宁可让调用线程变慢，也不静默丢弃访问数据。
- PV 使用 Hash 自增，UV 使用 HyperLogLog，避免 Set 存储全量 IP 造成内存膨胀。
- 定时落库使用 `ON DUPLICATE KEY UPDATE pv = GREATEST(...)`，任务重跑或并发执行不会让统计值回退。
- 定时落库前抢 `lock:stats:flush` 分布式锁（`tryLock(0, 10s)`，抢不到直接返回），集群仅一个节点执行，避免多节点同时落库造成无谓的 MySQL 行锁竞争。

### 8.6 链接过期与状态一致性

- 缓存值保存绝对过期时间戳，避免依赖缓存 TTL 判断业务过期。
- 写入缓存时 TTL 取“缓存默认 TTL”和“距离过期剩余时间”的较小值。
- 已过期链接不写缓存，防止过期链接被反复命中；状态变更主动失效缓存。

### 8.7 安全防护

- 仅允许 `http/https`，拒绝 `javascript:`、`file:` 等协议与带 userInfo 的 URL。
- 拦截 `localhost`、`127.0.0.1`、`10.x`、`192.168.x`、`172.16~31.x`、`169.254.x`、`::1`。
- 拦截 IPv6 唯一本地地址（`fc00::/7`）与链路本地地址（`fe80::/10`），并识别 IPv4 映射的 IPv6 写法（如 `::ffff:127.0.0.1`），防止 SSRF 绕过内网拦截。
- 支持域名黑名单与可选白名单，创建和跳转两个阶段都校验，防止缓存期内绕过。
- 管理接口令牌常量时间比较，生产环境建议叠加网关鉴权和内网访问控制。

## 9. 技术亮点

1. **雪花算法 + Base62 + 预生成池**：兼顾唯一性、短码长度与创建吞吐。
2. **Redisson 锁分层**：池补充锁、短码落库锁、缓存重建锁各自独立，锁粒度小。
3. **缓存三问题完整处理**：空值缓存、重建锁、TTL 抖动，并有缓存与业务过期时间对齐。
4. **Redis + Lua 原子限流**：双层维度限流，异常降级不影响核心跳转。
5. **异步统计 + HyperLogLog + 幂等落库**：低成本支撑 PV/UV，历史数据可重复计算。
6. **安全闭环**：限流、IP/域名黑名单、内网地址拦截、白名单、统一异常码。
7. **可观测与可运维**：统一 Result、错误码、`Retry-After`、关键链路日志、Nacos 配置化。

## 10. 部署说明

### 10.1 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.x
- Redis 7.x
- Nacos 2.x（可选）

### 10.2 初始化数据库

```bash
mysql -uroot -p < sql/schema.sql
```

### 10.3 启动中间件

```bash
# Redis
redis-server

# MySQL 需保证库 swift_link 已创建
# Nacos（可选）
sh startup.sh -m standalone
```

### 10.4 配置

`src/main/resources/application.yml` 已提供本地默认配置，常用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3306` | MySQL 地址 |
| `MYSQL_DB` | `swift_link` | 数据库名 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `root` / `root` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `SHORT_LINK_DOMAIN` | `http://s.link` | 短链对外域名 |
| `ADMIN_TOKEN` | `change-me` | 管理接口令牌，生产必须修改 |
| `NACOS_ENABLED` | `false` | 是否启用 Nacos 注册与配置 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 地址 |
| `SNOWFLAKE_WORKER_ID` | `1` | 集群内每个实例必须唯一 |
| `SNOWFLAKE_DATACENTER_ID` | `1` | 机房编号 |

启用 Nacos 时：

```bash
export NACOS_ENABLED=true
export NACOS_SERVER_ADDR=127.0.0.1:8848
```

配置中心 DataId 为 `swift-link-service.yaml`，可通过 Nacos 覆盖 `swift-link.*` 配置。

### 10.5 构建与运行

```bash
mvn clean package
java -jar target/swift-link-service-1.0.0.jar
```

### 10.6 集群部署要点

1. 每个实例的 `SNOWFLAKE_WORKER_ID` 必须不同（0~31），否则可能生成重复 ID。
2. 多实例共享同一个 Redis 与 MySQL，短码池、缓存、限流、黑名单天然全局一致。
3. Nacos 注册后由网关或 Nginx 负载均衡；短链域名解析到网关。
4. 生产环境建议将 `swift-link.allow-hosts` 配置为业务域名白名单。
5. 黑名单管理接口只暴露在内网或由网关鉴权，避免 `X-Admin-Token` 泄露。

### 10.7 测试

```bash
mvn test
```

包含 Base62 编解码、雪花 ID 唯一性、URL 安全校验、创建短链主流程共 10 个单元测试，不依赖外部中间件。

## 11. 简历可用项目描述

### 11.1 完整版

> **分布式短链接跳转服务（Spring Boot 3.2 / Java 17 / Redis / Redisson / MySQL / Nacos）**
>
> - 面向营销短信与推广页面场景，独立设计并实现短链生成、302 跳转、访问统计、过期失效与安全风控模块，支持多实例集群部署。
> - 短码采用雪花算法 + Base62 编码，避免自增 ID 暴露业务量；基于 Redis List 实现短码预生成池，创建链路只做 `LPOP`，并通过 Redisson 锁 + MySQL 唯一索引保证集群下短码全局唯一。
> - 设计 Cache-Aside 跳转缓存，使用空值缓存防穿透、Redisson 重建锁防击穿、TTL 随机抖动防雪崩，并将缓存 TTL 与链接绝对过期时间对齐，保证停用/过期状态及时生效。
> - 基于 Redis + Lua 实现 IP、短码双层固定窗口限流，脚本内原子完成计数与过期设置；结合 IP/域名黑名单、内网地址拦截与可选域名白名单，防止恶意刷量和开放重定向滥用。
> - 访问日志通过独立线程池异步落库，PV 使用 Redis Hash 计数、UV 使用 HyperLogLog 估算，定时任务每 5 分钟幂等落库至天级统计表，跳转主链路不受统计写入影响。
> - 使用统一错误码、全局异常处理、Nacos 注册与配置中心，完成 SQL 建表、配置外置与单元测试，项目可直接构建运行。

### 11.2 精简版

> **分布式短链接服务（Spring Boot 3.2 / Redis / Redisson / MySQL / Nacos）**
>
> - 使用雪花算法 + Base62 + Redis 预生成池生成全局唯一短码，Redisson 锁与唯一索引双重防冲突。
> - 实现 302 跳转与 Cache-Aside 缓存，通过空值缓存、重建锁、TTL 抖动解决穿透/击穿/雪崩。
> - 基于 Redis + Lua 完成 IP/短码双层限流，结合黑名单、内网地址拦截保障跳转安全。
> - 访问日志异步落库，PV/UV 使用 Hash + HyperLogLog 统计并定时幂等落库，支撑多实例部署。

## 12. 后续可扩展方向

- 将短码池与统计落库改为独立任务服务，进一步降低业务实例负担。
- 使用滑动窗口或令牌桶替换固定窗口限流，平滑窗口边界流量。
- 接入布隆过滤器预判短码是否存在，进一步降低空值缓存开销。
- 黑名单与白名单持久化到 MySQL 并提供审计日志。
- 增加 Prometheus 指标与链路追踪，完善生产可观测性。
