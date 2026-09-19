import cn.bugstack.ai.infrastructure.adapter.repository.RagUpdateRepository;
import cn.bugstack.ai.infrastructure.dao.IAiAgentDao;
import cn.bugstack.ai.infrastructure.dao.IAiAgentTaskScheduleDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientAdvisorDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientModelDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientRagOrderDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientSystemPromptDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientToolMcpDao;
import cn.bugstack.ai.infrastructure.dao.IAiRagUpdateTaskDao;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.List;

/**
 * 验证统计接口改造后的 DAO 查询层。
 * <p>
 * 背景：`AiAgentDataStatisticsAdminController` 把 7 处 `dao.queryAll().size()` 改成了 `dao.selectCount(null)`，
 * 并新增了 `IAiAgentTaskScheduleDao.countEnabledTasks()`。这个改动直接决定管理端首页
 * （`/api/v1/admin/data/statistics/get-data-statistics`）会不会 500，
 * 而只读 MyBatis-Plus 字节码只能推出「模板里有判空」，推不出「一定不抛异常」。
 * <p>
 * 本程序做两件事：
 * <ol>
 *   <li><b>不连库</b>渲染动态 SQL —— 出问题的正是 {@code <if test="ew != null">} 那一步的 OGNL 求值，渲染通过即证明不 NPE；</li>
 *   <li><b>真连库</b>执行，并把新写法与旧写法逐一对账 —— 结果一致即证明重构无行为变更。</li>
 * </ol>
 * 运行方式见 docs/verify/README.md。
 */
public class DaoCountSqlVerify {

    private static final String JDBC_URL =
            "jdbc:mysql://127.0.0.1:13306/ai-agent-station-study"
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";

    /** 统计接口里用到的 7 个 DAO，逐个对账 selectCount(null) 与 queryAll().size() */
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        DataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                JDBC_URL, "root", "123456");
        configuration.setEnvironment(new Environment("verify", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);

        List<Class<?>> allDaos = List.of(
                IAiAgentDao.class, IAiClientDao.class, IAiClientToolMcpDao.class,
                IAiClientSystemPromptDao.class, IAiClientRagOrderDao.class,
                IAiClientAdvisorDao.class, IAiClientModelDao.class,
                IAiAgentTaskScheduleDao.class, IAiRagUpdateTaskDao.class);
        for (Class<?> dao : allDaos) {
            configuration.addMapper(dao);
        }
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        MybatisConfiguration built = (MybatisConfiguration) factory.getConfiguration();

        // ---------- 场景 A：不连库，只渲染 SQL，验证 <if test="ew != null"> 不会 NPE ----------
        section("场景 A：selectCount(null) 动态 SQL 渲染（不连库）");
        for (Class<?> dao : allDaos) {
            if (dao == IAiAgentTaskScheduleDao.class) {
                continue;
            }
            render(built, dao, "selectCount", ewNullParam(), "ew=null");
        }
        section("场景 B：selectCount 的 parameterObject 直接传 null（最坏情况）");
        render(built, IAiAgentDao.class, "selectCount", null, "parameterObject=null");

        // ---------- 场景 C：真连库执行，新写法 vs 旧写法逐一对账 ----------
        section("场景 C：真连库执行 —— selectCount(null) 是否等于 queryAll().size()");
        try (SqlSession session = factory.openSession()) {
            check(session, IAiAgentDao.class, "ai_agent");
            check(session, IAiClientDao.class, "ai_client");
            check(session, IAiClientToolMcpDao.class, "ai_client_tool_mcp");
            check(session, IAiClientSystemPromptDao.class, "ai_client_system_prompt");
            check(session, IAiClientRagOrderDao.class, "ai_client_rag_order");
            check(session, IAiClientAdvisorDao.class, "ai_client_advisor");
            check(session, IAiClientModelDao.class, "ai_client_model");
        } catch (Throwable t) {
            fail++;
            System.out.println("  FAIL  连库执行失败：" + t.getClass().getName() + ": " + t.getMessage());
            System.out.println("         （若数据库未启动，属环境问题，非代码问题）");
        }

        // ---------- 场景 D：新增的 DAO default 方法 ----------
        section("场景 D：countEnabledTasks() 是否等于 queryEnabledTasks().size()");
        try (SqlSession session = factory.openSession()) {
            IAiAgentTaskScheduleDao dao = session.getMapper(IAiAgentTaskScheduleDao.class);
            long viaCount = dao.countEnabledTasks();
            long viaList = dao.queryEnabledTasks().size();
            report(viaCount == viaList, IAiAgentTaskScheduleDao.class, "countEnabledTasks",
                    "count(status=1)=" + viaCount + " vs queryEnabledTasks().size()=" + viaList,
                    viaCount == viaList ? "一致" : "不一致！");
        } catch (Throwable t) {
            fail++;
            System.out.println("  FAIL  连库执行失败：" + t.getClass().getName() + ": " + t.getMessage());
        }

        // ---------- 场景 E：新增的 queryTaskRagIds ----------
        checkTaskRagIds(factory);

        section("汇总");
        System.out.printf("通过 %d 项，失败 %d 项%n", pass, fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    /**
     * 场景 E：验证新增的 {@code queryTaskRagIds}。
     * <p>
     * 它是 {@code retryFailedTask} 的修复关键 —— 原实现从内存 Map 取 ragIds，而该 Map 已在上一轮
     * finally 中清理，导致重试时取不到、任务永远卡在 PENDING。
     * <p>
     * 插入后**回滚**（session 非自动提交），不留任何数据痕迹。
     */
    private static void checkTaskRagIds(SqlSessionFactory factory) {
        section("场景 E：queryTaskRagIds 从库取回 ragIds（事务内插入 + 回滚，不留痕）");
        SqlSession session = factory.openSession();
        try {
            RagUpdateRepository repository = new RagUpdateRepository();
            // RagUpdateRepository 是 @Repository，字段靠 @Resource 注入；这里不起容器，用反射补
            Field field = RagUpdateRepository.class.getDeclaredField("aiRagUpdateTaskDao");
            field.setAccessible(true);
            field.set(repository, session.getMapper(IAiRagUpdateTaskDao.class));

            String taskId = "verify_" + System.currentTimeMillis();
            List<String> expected = List.of("rag-1001", "rag-1002", "rag-1003");
            repository.createUpdateTask(taskId, expected, "verify");

            List<String> actual = repository.queryTaskRagIds(taskId);
            report(expected.equals(actual), RagUpdateRepository.class, "queryTaskRagIds",
                    "写入=" + expected + " 读回=" + actual,
                    expected.equals(actual) ? "一致（逗号拼接可正确还原）" : "不一致！");

            // 边界 1：任务不存在 → 返回空列表而不是 null
            List<String> missing = repository.queryTaskRagIds("no_such_task_id_" + System.nanoTime());
            report(missing != null && missing.isEmpty(), RagUpdateRepository.class, "queryTaskRagIds",
                    "不存在的 taskId → " + missing,
                    "返回空列表（调用方无需判空）");

            // 边界 2：ragIds 为空串（createUpdateTask 用 String.join 会得到 ""）
            String emptyTaskId = "verify_empty_" + System.currentTimeMillis();
            repository.createUpdateTask(emptyTaskId, List.of(), "verify");
            List<String> empty = repository.queryTaskRagIds(emptyTaskId);
            report(empty != null && empty.isEmpty(), RagUpdateRepository.class, "queryTaskRagIds",
                    "空 ragIds → " + empty,
                    "返回空列表（不抛异常）");
        } catch (Throwable t) {
            report(false, RagUpdateRepository.class, "queryTaskRagIds", "-",
                    t.getClass().getName() + ": " + t.getMessage());
        } finally {
            session.rollback();   // 关键：插入的行不落库
            session.close();
        }
    }

    /** 真连库执行：新写法 selectCount(null) 与旧写法 queryAll().size() 必须相等 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void check(SqlSession session, Class<?> daoClass, String tableName) {
        try {
            BaseMapper mapper = (BaseMapper) session.getMapper(daoClass);
            long viaCount = mapper.selectCount(null);
            // queryAll() 是各 DAO 自己声明的 default 方法，BaseMapper 上没有，只能反射调
            List<?> all = (List<?>) daoClass.getMethod("queryAll").invoke(mapper);
            long viaList = all.size();
            boolean ok = viaCount == viaList;
            report(ok, daoClass, "selectCount(null)",
                    "count=" + viaCount + " vs queryAll().size()=" + viaList,
                    ok ? "一致（表 " + tableName + "）" : "不一致！");
        } catch (Throwable t) {
            report(false, daoClass, "selectCount(null)", tableName,
                    t.getClass().getName() + ": " + t.getMessage());
        }
    }

    /** MP 的 MapperMethod 会给 selectCount 传一个 key 为 ew、值为 null 的 ParamMap */
    private static MapperMethod.ParamMap<Object> ewNullParam() {
        MapperMethod.ParamMap<Object> paramMap = new MapperMethod.ParamMap<>();
        paramMap.put("ew", null);
        paramMap.put("param1", null);
        return paramMap;
    }

    private static void render(MybatisConfiguration configuration, Class<?> dao,
                               String methodName, Object parameter, String label) {
        String id = dao.getName() + "." + methodName;
        try {
            MappedStatement ms = configuration.getMappedStatement(id);
            if (ms == null) {
                report(false, dao, methodName, label, "MappedStatement 不存在（方法未注册）");
                return;
            }
            BoundSql boundSql = ms.getBoundSql(parameter);
            report(true, dao, methodName, label, boundSql.getSql().replaceAll("\\s+", " ").trim());
        } catch (Throwable t) {
            report(false, dao, methodName, label, t.getClass().getName() + ": " + t.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(96));
        System.out.println(title);
        System.out.println("=".repeat(96));
    }

    private static void report(boolean ok, Class<?> dao, String methodName, String label, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-26s %-18s [%s]%n    %s%n",
                ok ? "  OK  " : " FAIL ", dao.getSimpleName(), methodName, label, detail);
    }
}
