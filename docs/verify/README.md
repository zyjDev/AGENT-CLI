# docs/verify —— 不打包也能验证的独立程序

本目录放**不需要打包、不需要重启服务**就能跑的验证程序。
用于「改了代码但用户要求不打包」的场景：既守住约束，又不把改动留在未验证状态。

---

## DaoCountSqlVerify —— 统计接口 DAO 查询层验证

### 为什么要写它

`AiAgentDataStatisticsAdminController` 把 7 处 `dao.queryAll().size()` 改成了 `dao.selectCount(null)`，
并新增了 `IAiAgentTaskScheduleDao.countEnabledTasks()`、`IRagUpdateRepository.queryTaskRagIds()`。
这三处直接决定管理端首页和 RAG 任务重试是否正常，但：

- 读 MyBatis-Plus 字节码只能推出「SQL 模板里有判空」，**推不出「一定不抛异常」**；
- 旧 jar 仍在 8099 上跑，改动的代码从未被执行过。

所以用一个独立程序把「结论」变成「实测」。

### 五个场景

| 场景 | 内容 | 要不要连库 |
|---|---|---|
| A | 7 个 DAO 的 `selectCount(null)` 动态 SQL 渲染 —— 出问题的正是 `<if test="ew != null">` 那一步的 OGNL 求值 | 否 |
| B | `selectCount` 的 `parameterObject` 直接传 `null`（最坏情况） | 否 |
| C | **真连库**执行 `selectCount(null)`，与旧写法 `queryAll().size()` **逐一对账** | 是 |
| D | `countEnabledTasks()` 与 `queryEnabledTasks().size()` 对账 | 是 |
| E | `queryTaskRagIds` 从库取回 ragIds + 两个边界（不存在 / 空串） | 是 |

场景 C/D 是**最有说服力的断言**：新旧写法结果一致，等价于证明「这次重构无行为变更」。

场景 E 会插入任务行，但**整个操作在一个非自动提交的事务里，最后 `session.rollback()`** ——
不留任何数据痕迹（已实测：`ai_rag_update_task` 行数仍为 0）。

### 运行步骤

```bash
cd "E:/Study_Project/ai-agent-station-study"

# ① 生成依赖 classpath（只需一次；改过 pom 后重跑）
java -classpath "D:/Maven/apache-maven-3.9.14/boot/plexus-classworlds-2.9.0.jar" \
  -Dclassworlds.conf="D:/Maven/apache-maven-3.9.14/bin/m2.conf" \
  -Dmaven.home="D:/Maven/apache-maven-3.9.14" \
  -Dmaven.multiModuleProjectDirectory="E:/Study_Project/ai-agent-station-study" \
  org.codehaus.plexus.classworlds.launcher.Launcher \
  -pl ai-agent-station-study-infrastructure -am dependency:build-classpath \
  "-Dmdep.outputFile=E:/Study_Project/ai-agent-station-study/docs/verify/cp-infra.txt"

# ② 组装完整 classpath：模块 target/classes + 过滤后的依赖 + mysql 驱动
CP=$(tr ';' '\n' < docs/verify/cp-infra.txt \
     | grep -v -E 'logback|spring-boot-|log4j|jul-to-slf4j|slf4j-(simple|jdk14|nop)' \
     | sed 's|\\|/|g' | paste -sd ';' -)
MODS="ai-agent-station-study-types/target/classes;ai-agent-station-study-api/target/classes;\
ai-agent-station-study-domain/target/classes;ai-agent-station-study-infrastructure/target/classes"
MYSQL="E:/zzkj/soft/maven/repository/mysql/mysql-connector-java/8.0.28/mysql-connector-java-8.0.28.jar"
printf '%s;%s;%s' "$MODS" "$CP" "$MYSQL" > docs/verify/cp-full.txt

# ③ 编译 + 运行（用 argfile 传 classpath，避免 Git Bash 的 MSYS 路径转换破坏 ';'）
printf -- '-encoding\nUTF-8\n-cp\n"%s"\n-d\ndocs/verify/out\ndocs/verify/DaoCountSqlVerify.java\n' \
  "$(cat docs/verify/cp-full.txt)" > docs/verify/javac-args.txt
mkdir -p docs/verify/out && javac @docs/verify/javac-args.txt

printf -- '-cp\n"docs/verify/out;%s"\nDaoCountSqlVerify\n' \
  "$(cat docs/verify/cp-full.txt)" > docs/verify/java-args.txt
java @docs/verify/java-args.txt
```

### 已踩过的坑

1. **mysql 驱动不在 infrastructure 的依赖里** —— 它声明在 `app` 模块，所以 `-pl infrastructure`
   生成的 classpath 里没有，必须手工把 jar 追加进去（`cp-full.txt` 末尾）。
2. **classpath 有 19KB，必须用 argfile**（`javac @args.txt` / `java @args.txt`）：
   Git Bash 的 MSYS 路径转换会破坏 `;` 分隔的 classpath，且 Windows 命令行长度有限。
3. **`report()` 的第二个参数是 `Class<?>`**，传字符串会编译报错（本人连犯两次）。
   新增断言时直接传 `XxxDao.class`。
4. **`default` 方法不会被注册成 MappedStatement** —— `countEnabledTasks()` 是接口 `default` 方法，
   `configuration.getMappedStatement("...countEnabledTasks")` 必然返回 null。
   要验证它只能真连库调用（场景 D 的做法）。
5. **`RagUpdateRepository` 是 `@Repository`，字段靠 `@Resource` 注入** —— 不起容器就要用
   `Field.setAccessible(true)` 反射补上依赖（场景 E 的做法）。

### 期望输出

```
通过 20 项，失败 0 项
```

任一项 FAIL 会 `System.exit(1)`，便于接进 CI。
