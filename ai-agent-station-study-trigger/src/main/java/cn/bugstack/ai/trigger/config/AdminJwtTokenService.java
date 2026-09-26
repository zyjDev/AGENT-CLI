package cn.bugstack.ai.trigger.config;

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

    public String createToken(String userId, String username) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(userId)
                .withClaim("username", username)
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

    /** 从已解码的 token 里取用户名（claim），缺失时返回空串 */
    public String usernameOf(DecodedJWT jwt) {
        String username = jwt.getClaim("username").asString();
        return username == null ? "" : username;
    }
}
