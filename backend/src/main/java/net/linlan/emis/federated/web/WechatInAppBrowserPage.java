package net.linlan.emis.federated.web;

/**
 * 微信内置浏览器引导页：固定 HTML，不渲染扫码组件、不返回 5xx。
 */
public final class WechatInAppBrowserPage {

    private WechatInAppBrowserPage() {
    }

    public static String render(String returnUrl) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>请在系统浏览器打开</title>
                  <style>
                    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0;padding:24px;color:#1f2328;background:#f6f8fa}
                    .card{max-width:520px;margin:12vh auto;background:#fff;border:1px solid #d0d7de;border-radius:12px;padding:28px}
                    h1{font-size:18px;margin:0 0 12px}
                    p{line-height:1.6;color:#57606a;margin:8px 0}
                    code{display:block;word-break:break-all;background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;padding:10px;margin:14px 0;color:#1f2328}
                    a{color:#0969da}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>请在系统浏览器中打开</h1>
                    <p>微信内置浏览器不支持第三方扫码授权，请复制下方链接到系统浏览器（Safari / Chrome）后继续。</p>
                    <code>%s</code>
                    <p>也可以直接使用<a href="/login">账号密码登录</a>。</p>
                  </div>
                </body>
                </html>
                """.formatted(escape(returnUrl == null ? "/" : returnUrl));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}