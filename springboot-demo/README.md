# 🍃 SpringBoot · 机制验证实验室

**SpringBoot 3.3.5 机制验证 — demo01~19，配套 tech-knowledge-docs/docs/07-springboot 八篇**

_从 API 进入，穿透源码与运行时；文档讲因果，这里负责跑出证据_

> 📚 **知识库** → [docs/07-springboot](https://github.com/imZhiYa/tech-knowledge-docs/tree/main/docs/07-springboot) 八篇
> 🧬 **本模块** → 19 组 demo，覆盖 IoC 手写、事件、自动装配、Web 双跑法、事务、AOP、生产排障

---

## 📦 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21（编译 target 21，Azul/Temurin 均可） |
| Maven | 3.8+（依赖管理 + 双跑法 classpath 过滤） |
| 联网 | 首次构建需拉取 Spring Boot 3.3.5 依赖 |

> ⚠️ 依赖由 `pom.xml` 管理，版本由 `spring-boot-starter-parent 3.3.5` 统一锁定（与知识库实验环境 lib/ 的 43 个 jar 同版本）。

---

## 🚀 快速开始

```bash
# 编译 + 生成 classpath（首次）
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt

# 运行任意 demo
./run.sh demo01.MyMiniIoCApp

# WebFlux 双跑法：过滤 webmvc+tomcat 后 Boot 走 REACTIVE 分支
./run.sh -reactive demo11.webflux.WebFluxApp

# 优雅停机对照实验（immediate | graceful | graceful-timeout）
./scripts/graceful-shutdown-test.sh graceful
```

`run.sh` 做了三件事：检测未编译则自动 `mvn compile`、复用 `target/cp.txt`、`-reactive` 时从 classpath 剔除 `spring-webmvc` 与 `tomcat-embed` 使 Boot 的 `WebApplicationType` 探测落到 REACTIVE。

---

## 🗺️ Demo 索引（demo → 验证点 → 对应文档）

### 认知骨架（第 00 篇）
| Demo | 验证点 |
|---|---|
| `demo01.MyMiniIoCApp` | 手写 IoC：单例池、构造器依赖注入——先造轮子再读源码 |
| `demo01.DefaultListableBeanFactoryApp` | 真实 `DefaultListableBeanFactory` 的 bean 注册/获取/覆盖行为 |
| `demo01.ServiceLoaderApp` | SPI 扩展加载（`META-INF/services`） |

### 框架集成：Bean 生命周期与注入（第 01 篇）
| Demo | 验证点 |
|---|---|
| `demo02.AwareFamilyApp` | Aware 家族回调触发时机与顺序（`aware.properties` 驱动） |
| `demo02.LifecycleApp` | 初始化/销毁生命周期钩子执行链 |
| `demo02.FullVsLiteApp` | 全注解 `@Configuration` vs Lite 模式代理差异 |
| `demo03.ValueApp` | `@Value` + `@PropertySource`（`demo03/app.properties`） |
| `demo03.ResolutionChainApp` | 占位符解析链与默认值 |
| `demo03.ObjectProviderApp` | 延迟/可选注入 |
| `demo04.*` | 循环依赖四态：构造器失败、字段兜底、`@Lazy` 破解、`allow-circular-references` 开关 |

### 事件体系（第 02 篇）
| Demo | 验证点 |
|---|---|
| `demo08.EventSyncApp` | 同步事件：监听器按序执行、异常传播 |
| `demo08.GenericEventApp` | 泛型事件类型匹配 |
| `demo08.AsyncEventApp` | `@Async` 异步事件与线程池 |
| `demo08.EarlyEventApp` | `run()` 早期事件（ApplicationStartingEvent 等） |
| `demo08.TransactionalEventApp` | 事务事件（`@TransactionalEventListener`） |
| `demo09.BootEventsApp` | 启动全生命周期事件广播顺序打点 |

### 自动装配与条件（第 03 篇）
| Demo | 验证点 |
|---|---|
| `demo10.BeanRegisterWaysApp` | 5 种 Bean 注册方式对照（含 `@ImportResource` 读 XML） |
| `demo10.ClassConditionApp` / `PropertyConditionApp` | `@ConditionalOnClass` / `@ConditionalOnProperty` 判定 |
| `demo10.ExclusionApp` / `OrderingApp` / `OverrideApp` | 排除、装配顺序、Bean 覆盖策略 |
| `demo10.ReportApp` | 装配报告 |
| `demo07.TranslatorApp` | 自定义注解 + `ImportBeanDefinitionRegistrar` 扩展实战 |
| `demo17.PermissionStarterApp` | 仿 starter：自动配置类 + 条件注解 + AOP 切面（`META-INF/spring/...AutoConfiguration.imports`） |

### Web 请求与运行时（第 04 篇）
| Demo | 验证点 |
|---|---|
| `demo06.MinimalBootApp` | 最小启动：SpringApplication.run 裸跑 |
| `demo11.RunTraceApp` | `run()` 全流程阶段打点（javap 字节码实证） |
| `demo11.WebFluxApp` | **双跑法**：同一代码，SERVLET（Tomcat）与 REACTIVE（Netty）两条分支 |
| `demo11.ActuatorApp` | Actuator 端点与指标 |
| `demo11.WebTraceApp` | Web 请求链路 |

### 事务与数据层（第 05 篇）
| Demo | 验证点 |
|---|---|
| `demo12.DataSourceApp` | H2 + HikariCP 数据源装配与连接 |
| `demo12.TxBasicsApp` | `@Transactional` 提交/回滚基础行为 |
| `demo12.PropagationApp` | 传播行为矩阵（REQUIRED/REQUIRES_NEW/NESTED…） |
| `demo12.SelfInvocationApp` | **自调用失效**：this 调用绕过代理的实证 |

### AOP（第 06 篇）
| Demo | 验证点 |
|---|---|
| `demo13.AdviceOrderApp` | 环绕/前置/后置通知执行顺序 |
| `demo13.AspectOrderApp` | 多切面 `@Order` 排序 |
| `demo13.PointcutApp` | 切入点表达式 |
| `demo13.ProxyInternalsApp` | JDK 动态代理 vs CGLIB 内部结构 |
| `demo13.UnwrapApp` / `VisibilityApp` / `ProxyKindApp` | 代理解包、可见性、代理类型判定 |
| `demo13.TxVisibilityApp` | 事务注解对代理的可见性 |

### 生产实践：排障与优雅（第 07 篇）
| Demo | 验证点 |
|---|---|
| `demo14.TriageApp` | 故障定位清单 |
| `demo14.StartupTimerApp` | 启动耗时粗定位（基础版） |
| `demo15.RefreshFailApp` | 上下文刷新失败现场 |
| `demo15.BootCircularApp` | 启动期循环依赖崩溃 |
| `demo16.GracefulShutdownApp` | 优雅停机：`server.shutdown=graceful` 在途请求 vs immediate 杀停 |
| `demo18.AotGenerationApp` | AOT：`ApplicationAotGenerator` 生成骨架源码 |
| `demo19.buffered.StartupProfilerApp` | **启动慢排查四板斧之三**：`BufferingApplicationStartup` + actuator `/startup` 端点，360 步事件按耗时 Top 15 |
| `demo19.jfr.JfrStartupApp` | **启动慢排查四板斧之四**：JFR 录制启动事件 |

> 启动慢排查四板斧完整演示见 [docs/07-springboot/springboot-07-production-practice.md](https://github.com/imZhiYa/tech-knowledge-docs/blob/main/docs/07-springboot/springboot-07-production-practice.md)：actuator 端点 → BufferingApplicationStartup → `/startup` 访问 → JFR。

---

## 📁 目录结构

```
springboot-demo/
├── pom.xml                     # starter-web/actuator/aop/jdbc/webflux + H2，版本 parent 锁定
├── run.sh                      # 运行器（含 -reactive 双跑法过滤）
├── scripts/
│   └── graceful-shutdown-test.sh   # 优雅停机三模式对照
└── src/
    ├── main/java/              # 按 demo01~19 分包，86 个源文件
    └── main/resources/
        ├── application.properties
        ├── demo03/app.properties         # @PropertySource 资源
        ├── demo02/aware.properties
        ├── demo10/{way,destroy}/beans.xml
        └── META-INF/
            ├── services/demo01.spi.Greeter            # SPI 注册
            └── spring/...AutoConfiguration.imports    # 自动装配注册
```

> 资源路径全部为 classpath 语义（`classpath:demo03/app.properties` 等），与源码位置解耦，`mvn package` 后同样可用。

---

## 🧪 双跑法说明（demo11）

Boot 启动时通过 `WebApplicationType.deduceFromClasspath()` 探测：

| classpath 特征 | 分支 |
|---|---|
| 含 `spring-webmvc` + `tomcat-embed-core` | SERVLET（`AnnotationConfigServletWebServerApplicationContext`） |
| 不含 webmvc，含 `webflux` + `reactor-netty` | REACTIVE（`AnnotationConfigReactiveWebServerApplicationContext`） |

```bash
./run.sh demo11.webflux.WebFluxApp          # → SERVLET
./run.sh -reactive demo11.webflux.WebFluxApp # → REACTIVE
```

---

## ✅ CI

`verify-lab.yml` 中 `springboot-demo` 归入 JDK 21 专区：push/PR 自动 `mvn compile` 并跑代表性 demo 冒烟验证。
