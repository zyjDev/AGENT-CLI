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
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            return StringUtils.hasText(jwt.getSubject());
        } catch (JWTVerificationException e) {
            log.debug("Invalid admin token: {}", e.getMessage());
            return false;
        }
    }
}
