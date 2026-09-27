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

        // 账号管理接口整段归管理员：登录 / 注册 / 校验 / 改自己密码 四个入口除外。
        // 放在拦截器而不是每个 Controller 方法里，避免以后新增账号接口时漏加。
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/admin/admin-user/")
                && !uri.endsWith("/login")
                && !uri.endsWith("/register")
                && !uri.endsWith("/validate-login")
                // 改密码是"任何登录用户"的权利，只是不能改别人的（服务端只认 JWT 里的 userId）
                && !uri.endsWith("/change-password")
                && !UserContext.isAdmin()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JSON.toJSONString(Response.error("该操作仅管理员可用")));
            return false;
        }

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
