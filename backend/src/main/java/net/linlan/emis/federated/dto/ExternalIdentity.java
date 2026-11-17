package net.linlan.emis.federated.dto;

/**
 * 第三方身份（provider 输出）。禁止包含 token / secret / 授权码字段。
 *
 * @param provider      提供方编码：github / wechat
 * @param subject       身份主键：github=数字用户ID；wechat=unionid
 * @param openId        微信 openid（仅最近一次；github 为 null）
 * @param displayName   昵称/login，仅展示
 * @param avatarUrl     头像地址
 * @param verifiedEmail 已验证邮箱（仅 GitHub 且具备 Email 读取权限时）
 * @param phone         手机号（本期恒 null）
 */
public record ExternalIdentity(String provider, String subject, String openId,
                               String displayName, String avatarUrl,
                               String verifiedEmail, String phone) {
}