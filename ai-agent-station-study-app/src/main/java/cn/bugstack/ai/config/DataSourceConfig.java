package cn.bugstack.ai.config;

import cn.bugstack.ai.trigger.support.AdminPageSupport;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@MapperScan(value = "cn.bugstack.ai.infrastructure.dao", sqlSessionFactoryRef = "sqlSessionFactory", sqlSessionTemplateRef = "sqlSessionTemplate")
public class DataSourceConfig {

    @Bean("mysqlDataSource")
    @Primary
    public DataSource mysqlDataSource(@Value("${spring.datasource.mysql.driver-class-name}") String driverClassName,
                                      @Value("${spring.datasource.mysql.url}") String url,
                                      @Value("${spring.datasource.mysql.username}") String username,
                                      @Value("${spring.datasource.mysql.password}") String password,
                                      @Value("${spring.datasource.mysql.hikari.maximum-pool-size:10}") int maximumPoolSize,
                                      @Value("${spring.datasource.mysql.hikari.minimum-idle:5}") int minimumIdle,
                                      @Value("${spring.datasource.mysql.hikari.idle-timeout:30000}") long idleTimeout,
                                      @Value("${spring.datasource.mysql.hikari.connection-timeout:30000}") long connectionTimeout,
                                      @Value("${spring.datasource.mysql.hikari.max-lifetime:1800000}") long maxLifetime) {
        // 连接池配置
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setPoolName("MainHikariPool");

        return dataSource;
    }

    /**
     * MyBatis-Plus 分页插件。
     *
     * <p>⚠️ 必须显式挂载：本项目的 {@code SqlSessionFactory} 是下面手工声明的
     * {@link MybatisSqlSessionFactoryBean}，<b>不是</b> Spring Boot starter 自动配置出来的那个，
     * 所以自动配置里的分页插件对本项目<b>不生效</b>。
     *
     * <p>漏挂的后果是「静默失效」：{@code selectPage} 不报错，但既不加 LIMIT 也不执行 count，
     * {@code total} 恒为 0、{@code records} 是整表数据 —— 看起来就像「分页没生效」。
     */
    @Bean("mybatisPlusInterceptor")
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 兜底：与 AdminPageSupport.MAX_PAGE_SIZE 对齐，防止 pageSize 传得过大把整表捞回来
        pagination.setMaxLimit((long) AdminPageSupport.MAX_PAGE_SIZE);
        // 页码越界（如记录被删后 pageNum 仍停在旧的大值）返回空列表，而不是悄悄回到第一页
        pagination.setOverflow(false);

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean("sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("mysqlDataSource") DataSource mysqlDataSource,
                                               @Autowired(required = false) MetaObjectHandler metaObjectHandler,
                                               @Qualifier("mybatisPlusInterceptor") MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean sqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(mysqlDataSource);

        // 注入 MyBatis-Plus 全局配置（元数据自动填充处理器等）；SQL 已由 MyBatis-Plus Wrapper 拼接，不再依赖 XML
        GlobalConfig globalConfig = new GlobalConfig();
        if (metaObjectHandler != null) {
            globalConfig.setMetaObjectHandler(metaObjectHandler);
        }
        sqlSessionFactoryBean.setGlobalConfig(globalConfig);

        // 分页插件挂到手工工厂上（原因见 mybatisPlusInterceptor 的注释）
        sqlSessionFactoryBean.setPlugins(mybatisPlusInterceptor);

        return sqlSessionFactoryBean.getObject();
    }

    @Bean("sqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource(@Value("${spring.datasource.pgvector.driver-class-name}") String driverClassName,
                                         @Value("${spring.datasource.pgvector.url}") String url,
                                         @Value("${spring.datasource.pgvector.username}") String username,
                                         @Value("${spring.datasource.pgvector.password}") String password,
                                         @Value("${spring.datasource.pgvector.hikari.maximum-pool-size:5}") int maximumPoolSize,
                                         @Value("${spring.datasource.pgvector.hikari.minimum-idle:2}") int minimumIdle,
                                         @Value("${spring.datasource.pgvector.hikari.idle-timeout:30000}") long idleTimeout,
                                         @Value("${spring.datasource.pgvector.hikari.connection-timeout:30000}") long connectionTimeout) {
        // 连接池配置
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);

        // 确保在启动时连接数据库
        dataSource.setInitializationFailTimeout(1);  // 设置为1ms，如果连接失败则快速失败
        dataSource.setConnectionTestQuery("SELECT 1"); // 简单的连接测试查询
        dataSource.setAutoCommit(true);
        dataSource.setPoolName("PgVectorHikariPool");
        return dataSource;
    }

    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

}
