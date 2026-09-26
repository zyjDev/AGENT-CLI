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

    private final Algorithm algorithm;
    private final long expireMinutes;

    public AdminJwtTokenService(
            @Value("${admin.jwt.secret:ai-agent-station-study-change-me-32-char-secret}") String secret,
            @Value("${admin.jwt.expire-minutes:720}") long expireMinutes) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expireMinutes = expireMinutes;
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
