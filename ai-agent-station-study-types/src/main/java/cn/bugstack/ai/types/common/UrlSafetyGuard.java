package cn.bugstack.ai.types.common;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 外呼地址安全校验（SSRF 防护）。
 *
 * ============================ 为什么需要它 ============================
 * 项目里有两条「地址由用户填写、由服务端发起请求」的链路：
 * <ol>
 *   <li>{@code ai_client_api.base_url} —— 装配成 {@code OpenAiApi} 后由服务端发起对话/向量化请求；</li>
 *   <li>MCP 的 sse {@code baseUri} —— 服务端作为客户端连过去。</li>
 * </ol>
 * 这两处此前**没有任何校验**：普通用户（自助注册即可）填一个内网地址，
 * 服务端就会带着自己的网络位置去访问它 —— 典型的 SSRF。
 * 云环境里 {@code http://169.254.169.254/...} 更是可以直接读到实例元数据（含临时凭证）。
 *
 * ============================ 校验口径 ============================
 * <ul>
 *   <li>协议只允许 http / https，且不允许携带 {@code user:pass@} 形式的用户信息；</li>
 *   <li>字面量 IP（含十进制整型、{@code [::1]} 这类写法）落在回环 / 私有 / 链路本地 /
 *       CGNAT / 组播 / 保留段 → 拒绝；</li>
 *   <li>主机名只按文本拦截 {@code localhost}、{@code *.localhost}、
 *       {@code metadata}、{@code metadata.google.internal}；</li>
 *   <li>数字外形却解析不出合法 IP 的（例如 {@code 999.999.999.999}）→ 一律拒绝（fail-closed）。</li>
 * </ul>
 *
 * <p><b>已知边界（有意为之）</b>：<b>不</b>对普通域名做 DNS 解析再去比对 IP ——
 * 那样会引入 DNS 查询开销、并把「域名解析结果可能变化」（DNS rebinding）当成安全边界，
 * 而且会让校验结果依赖运行环境的 DNS。域名解析带来的绕过风险，
 * 应由部署层（出网策略 / 反向代理白名单）兜底，不在这里假装解决。
 *
 * <p><b>管理员例外</b>：{@code allowPrivateHost=true} 时只做协议与主机名格式校验，
 * 放行内网地址 —— 平台默认资源由管理员维护，自建模型网关常常就在内网，这是正当场景；
 * 而普通用户（自助注册的外部人员）没有理由让服务端去访问内网。
 */
public final class UrlSafetyGuard {

    /** 拒绝时统一给用户的提示前缀，便于前端/排查时识别来源 */
    public static final String REJECT_PRIVATE = "不允许指向内网 / 本机 / 保留地址";

    private UrlSafetyGuard() {
    }

    /** 普通用户口径：禁用内网地址 */
    public static String checkHttpUrl(String url) {
        return checkHttpUrl(url, false);
    }

    /**
     * 校验一个「服务端将要访问」的 http(s) 地址。
     *
     * @param url              待校验地址
     * @param allowPrivateHost 是否允许指向内网 / 本机（仅限管理员维护平台默认资源的场景）
     * @return {@code null} = 通过；否则返回可直接展示给用户的原因
     */
    public static String checkHttpUrl(String url, boolean allowPrivateHost) {
        if (url == null || url.isBlank()) {
            return "地址不能为空";
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return "地址格式不正确";
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return "仅支持 http/https 协议";
        }
        if (uri.getUserInfo() != null) {
            return "地址不允许携带用户名密码";
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "地址缺少主机名";
        }

        if (allowPrivateHost) {
            return null;
        }
        return isBlockedHost(host) ? REJECT_PRIVATE + "（SSRF 防护）" : null;
    }

    /** 字面量 IPv4：a.b.c.d */
    private static final Pattern IPV4_DOTTED = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    /** 字面量 IPv6：至少一个冒号，且只含十六进制字符与点/冒号 */
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9a-fA-F]*:[0-9a-fA-F:.]+$");
    /** Java 的 InetAddress 接受「单个十进制数字」作为 IPv4 写法（如 2130706433），这类也要覆盖 */
    private static final Pattern PURE_DIGITS = Pattern.compile("^\\d+$");
    /** 十六进制写法（如 0x7f000001） */
    private static final Pattern HEX_FORM = Pattern.compile("^0[xX][0-9a-fA-F]+$");

    private static boolean isBlockedHost(String rawHost) {
        String host = normalizeHost(rawHost);
        if (host.isEmpty()) {
            return true;
        }
        if (isBlockedHostName(host)) {
            return true;
        }
        if (!looksLikeIpLiteral(host)) {
            // 普通域名：不做 DNS 解析（见类注释「已知边界」）
            return false;
        }
        try {
            return isBlockedAddress(InetAddress.getByName(host).getAddress());
        } catch (UnknownHostException e) {
            // 数字外形却解析不出合法 IP：fail-closed
            return true;
        }
    }

    /** 去掉 IPv6 方括号、结尾点，统一小写 */
    private static String normalizeHost(String rawHost) {
        String host = rawHost == null ? "" : rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    /** 主机名文本形式的黑名单（不做 DNS 解析，纯文本匹配） */
    private static boolean isBlockedHostName(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || "metadata".equals(host)
                || "metadata.google.internal".equals(host);
    }

    private static boolean looksLikeIpLiteral(String host) {
        return IPV4_DOTTED.matcher(host).matches()
                || IPV6_LITERAL.matcher(host).matches()
                || PURE_DIGITS.matcher(host).matches()
                || HEX_FORM.matcher(host).matches();
    }

    private static boolean isBlockedAddress(byte[] address) {
        if (address.length == 4) {
            return isBlockedIpv4(address);
        }
        return isBlockedIpv6(address);
    }

    private static boolean isBlockedIpv4(byte[] address) {
        int b0 = address[0] & 0xFF;
        int b1 = address[1] & 0xFF;

        if (b0 == 0) {
            return true;                                    // 0.0.0.0/8
        }
        if (b0 == 10) {
            return true;                                    // 10/8
        }
        if (b0 == 127) {
            return true;                                    // 回环 127/8
        }
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;                                    // CGNAT 100.64/10
        }
        if (b0 == 169 && b1 == 254) {
            return true;                                    // 链路本地 169.254/16（含云元数据 169.254.169.254）
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;                                    // 172.16/12
        }
        if (b0 == 192 && b1 == 168) {
            return true;                                    // 192.168/16
        }
        if (b0 == 192 && (b1 == 0 || b1 == 2)) {
            return true;                                    // 192.0.0/24、192.0.2/24
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;                                    // 198.18/15
        }
        if (b0 == 198 && b1 == 51) {
            return true;                                    // 198.51.100/24
        }
        if (b0 == 203 && b1 == 0) {
            return true;                                    // 203.0.113/24
        }
        return b0 >= 224;                                   // 组播 224/4 + 保留 240/4（含 255.255.255.255）
    }

    private static boolean isBlockedIpv6(byte[] address) {
        // 唯一本地地址 fc00::/7
        if ((address[0] & 0xFE) == 0xFC) {
            return true;
        }
        try {
            InetAddress inetAddress = InetAddress.getByAddress(address);
            return inetAddress.isAnyLocalAddress()      // ::
                    || inetAddress.isLoopbackAddress()  // ::1
                    || inetAddress.isLinkLocalAddress() // fe80::/10
                    || inetAddress.isSiteLocalAddress()
                    || inetAddress.isMulticastAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }

}
