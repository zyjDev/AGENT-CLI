package cn.bugstack.ai.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局把 Long 序列化成字符串。
 *
 * ============================ 为什么必须这么做 ============================
 * 主键改用雪花算法后是 19 位数字（例如 1834567890123456789），
 * 而 JS 的 Number 只能精确表示到 2^53-1（16 位）——
 * 直接以数字下发，前端拿到的值会被静默四舍五入（…789 → …780），
 * 于是「按 id 删除/更新/回查」会去找一条不存在的记录，且不报错，极难排查。
 * 序列化成字符串后，前端原样透传即可，彻底规避精度问题。
 *
 * 影响范围：所有 Long / long 字段（含统计计数），前端按字符串展示与回传均无影响。
 * ======================================================================
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
