package cn.bugstack.ai.trigger.config;

import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.types.context.UserContext;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端接口 JWT 鉴权拦截器
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminJwtTokenService adminJwtTokenService;

    public AdminAuthInterceptor(AdminJwtTokenService adminJwtTokenService) {
        this.adminJwtTokenService = adminJwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            token = authorization.substring(BEARER_PREFIX.length());
        }

        UserContext.LoginUser loginUser = adminJwtTokenService.verifyToLoginUser(token);
        if (loginUser == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JSON.toJSONString(Response.error("未登录或登录已过期")));
            return false;
        }

        // 落地当前用户：数据按用户隔离后，Controller 要靠它拿到 owner_id
        UserContext.set(loginUser);
        return true;
    }

    /**
     * 请求结束必须清理线程上下文 —— Tomcat 线程会被复用，不清就会把上一个请求的用户带给下一个。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
