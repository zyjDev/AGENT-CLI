package cn.bugstack.ai.trigger.config;

import cn.bugstack.ai.types.context.UserContext;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class AdminJwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtTokenService.class);

    /**
     * 历史硬编码默认值。
     * <p>
     * 2026-09-28 之前 {@code admin.jwt.secret} 从未在任何配置文件里出现过，
     * 因此这个默认值**在所有环境下都是实际生效的签名密钥**（仓库公开 = 密钥公开），
     * 任何人都能用它签出 role=admin 的 token。现在显式拒绝它，防止「照抄旧配置」再次上线。
     */
    private static final String LEGACY_DEFAULT_SECRET = "ai-agent-station-study-change-me-32-char-secret";

    /** 密钥长度下限。HMAC-SHA256 的安全下限是哈希输出长度（32 字节），这里按字符数近似校验 */
    private static final int MIN_SECRET_LENGTH = 32;

    private final Algorithm algorithm;
    /** token 有效期（分钟）。默认 720 分钟（12 小时）—— 「下次不用输账号密码」由前端预填实现，不靠长效 token */
    private final long expireMinutes;

    public AdminJwtTokenService(
            @Value("${admin.jwt.secret:}") String secret,
            @Value("${admin.jwt.expire-minutes:720}") long expireMinutes) {
        this.algorithm = Algorithm.HMAC256(assertSecret(secret));
        this.expireMinutes = expireMinutes;
    }

    /**
     * 校验签名密钥，不合格则**拒绝启动**（fail-fast）。
     * <p>
     * 刻意选择「启动即失败」而不是「回落默认值 + 打警告」：JWT 密钥缺失属于
     * 「静默降级为不安全但功能正常」的故障，只看日志很容易漏，只有起不来才必然被发现。
     *
     * @param secret 来自 {@code admin.jwt.secret} 的原始值
     * @return 校验通过的密钥
     */
    private static String assertSecret(String secret) {
        String hint = """

                ==== admin.jwt.secret 未正确配置，拒绝启动 ====
                原因：JWT 签名密钥缺失/过弱时，任何人都能伪造管理员 token（原有硬编码默认值已公开，等同无鉴权）。
                配置：在 application-dev.yml 中设置 admin.jwt.secret，或用环境变量 ADMIN_JWT_SECRET 注入。
                生成：openssl rand -hex 32        # Windows Git Bash 同样可用
                ============================================
                """;

        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(hint);
        }
        String trimmed = secret.trim();
        if (LEGACY_DEFAULT_SECRET.equals(trimmed)) {
            throw new IllegalStateException("\n检测到正在使用已公开的历史默认密钥，必须更换。" + hint);
        }
        if (trimmed.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "\nadmin.jwt.secret 长度不足 " + MIN_SECRET_LENGTH + " 字符（当前 " + trimmed.length() + "）。" + hint);
        }
        log.info("Admin JWT secret 已从配置注入（长度 {}），签发/校验将被启用", trimmed.length());
        return trimmed;
    }

    /**
     * 签发 token。
     * <p>
     * role 写进 claim：拦截器解析后放进 {@link UserContext}，用于判定能否修改「公共资源」。
     * ⚠️ 角色变更后需要重新登录才会生效（token 里是签发那一刻的角色）。
     */
    public String createToken(String userId, String username, String role) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role == null ? UserContext.ROLE_USER : role)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(expireMinutes, ChronoUnit.MINUTES)))
                .sign(algorithm);
    }

    public boolean validateToken(String token) {
        return verifyAndDecode(token) != null;
    }

    /**
     * 校验并解出 token 内容（subject = userId，claim username）。
     * <p>
     * 数据按用户隔离后，拦截器需要拿到 userId 才能落地 {@link UserContext}，
     * 而原先只有 boolean 的 {@link #validateToken(String)} 拿不到 subject，故补这个方法。
     *
     * @param token 原始 token（不带 Bearer 前缀）
     * @return 校验通过返回解码结果，否则 null
     */
    public DecodedJWT verifyAndDecode(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            if (!StringUtils.hasText(jwt.getSubject())) {
                return null;
            }
            return jwt;
        } catch (JWTVerificationException e) {
            log.debug("Invalid admin token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验并把 token 解成「当前登录用户」。
     * <p>
     * 拦截器只用这一个方法，就不必依赖 auth0 的 DecodedJWT 类型（依赖只留在本类里）。
     *
     * @param token 原始 token（不带 Bearer 前缀）
     * @return 校验通过返回登录用户，否则 null
     */
    public UserContext.LoginUser verifyToLoginUser(String token) {
        DecodedJWT jwt = verifyAndDecode(token);
        if (jwt == null) {
            return null;
        }
        // 老 token 没有 role claim：按普通用户处理（fail-closed），重新登录即可获得正确角色
        String role = jwt.getClaim("role").asString();
        return new UserContext.LoginUser(
                jwt.getSubject(),
                usernameOf(jwt),
                role == null ? UserContext.ROLE_USER : role);
    }

    /** 从已解码的 token 里取用户名（claim），缺失时返回空串 */
    private String usernameOf(DecodedJWT jwt) {
        String username = jwt.getClaim("username").asString();
        return username == null ? "" : username;
    }
}
