package com.synpharm.service.impl;

import com.synpharm.dto.response.SendCaptchaResult;
import com.synpharm.exception.BusinessException;
import com.synpharm.exception.ErrorCode;
import com.synpharm.service.CaptchaService;
import com.synpharm.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务实现
 *
 * <p>功能：
 * <ol>
 *   <li>生成6位随机数字验证码</li>
 *   <li>存入Redis，设置5分钟过期</li>
 *   <li>通过邮件服务发送验证码</li>
 *   <li>验证时从Redis取出比对</li>
 * </ol>
 *
 * <p>设计：
 * <ul>
 *   <li>验证码的发送通过 NotifyService 接口，不直接依赖邮件服务</li>
 *   <li>未来可以把邮箱验证码换成短信验证码，只需换个 NotifyService</li>
 *   <li>验证逻辑和发送逻辑解耦</li>
 * </ul>
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCaptchaServiceImpl implements CaptchaService {

    /** Redis操作 */
    private final StringRedisTemplate redisTemplate;

    /** 通知服务（依赖接口，不依赖具体实现） */
    private final NotifyService emailNotifyService;

    /** 发件邮箱（从 spring.mail.username 读取，与 JavaMailSender 共用同一配置；未配置则无法发真实邮件） */
    @Value("${spring.mail.username:}")
    private String senderEmail;

    /** 显式开发模式开关：true 时验证码直接回显给前端（仅限本地/测试，生产必须保持 false） */
    @Value("${CAPTCHA_DEV_MODE:false}")
    private boolean captchaDevMode;

    /** Redis Key前缀：验证码 */
    private static final String CAPTCHA_KEY = "captcha:email:";

    /** Redis Key前缀：发送频率限制 */
    private static final String LIMIT_KEY = "captcha:email:limit:";

    /** 验证码有效期（分钟） */
    private static final int CAPTCHA_EXPIRE_MINUTES = 1;

    /** 验证码长度 */
    private static final int CAPTCHA_LENGTH = 6;

    /** 每小时最多发送次数 */
    private static final int MAX_SEND_PER_HOUR = 5;

    /**
     * 发送邮箱验证码
     *
     * @param target 目标邮箱
     * @param type   验证码类型（login/register/reset等）
     */
    /** 允许的验证码类型 */
    private static final Set<String> ALLOWED_TYPES = Set.of("login", "register", "reset");

    @Override
    public SendCaptchaResult sendCaptcha(String target, String type) {
        // ========== 第〇步：类型白名单校验 ==========
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            log.warn("不支持的验证码类型: {}", type);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的验证码类型");
        }

        // ========== 第一步：频率限制检查 ==========
        checkSendLimit(target);

        // ========== 第二步：生成6位随机验证码 ==========
        String code = generateCaptcha();
        log.info("生成邮箱验证码, target: {}, type: {}", target, type);

        // ========== 第三步：存入Redis ==========
        String key = CAPTCHA_KEY + type + ":" + target;
        redisTemplate.opsForValue().set(key, code, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // ========== 第四步：发送邮件 ==========
        // 显式开发模式（CAPTCHA_DEV_MODE=true）才回显验证码；生产不允许回显
        if (captchaDevMode) {
            log.warn("[开发模式] CAPTCHA_DEV_MODE=true，验证码不回发邮件。target={}, code={}", target, code);
            return SendCaptchaResult.builder().devMode(true).code(code).build();
        }

        if (!StringUtils.hasText(senderEmail)) {
            log.error("未配置发件邮箱 QQ_EMAIL，无法发送验证码。target={}", target);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "邮件服务未配置，无法发送验证码");
        }

        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("minutes", String.valueOf(CAPTCHA_EXPIRE_MINUTES));
        emailNotifyService.send(target, "captcha", params);

        log.info("邮箱验证码发送成功, target: {}, type: {}", target, type);
        return SendCaptchaResult.builder().devMode(false).build();
    }

    /**
     * 验证邮箱验证码
     * <p>验证成功后删除验证码（一次性使用，防止重放攻击）。
     *
     * @param target 目标邮箱
     * @param code   用户输入的验证码
     * @param type   验证码类型
     * @return true=验证通过
     */
    @Override
    public boolean verifyCaptcha(String target, String code, String type) {
        if (code == null || code.length() != CAPTCHA_LENGTH) {
            return false;
        }

        String key = CAPTCHA_KEY + type + ":" + target;
        String savedCode = redisTemplate.opsForValue().get(key);

        // 验证码不存在或已过期
        if (savedCode == null) {
            log.warn("验证码不存在或已过期, target: {}", target);
            return false;
        }

        // 验证码不匹配（使用常量时间比较，防止时序攻击）
        if (!MessageDigest.isEqual(
                savedCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8))) {
            log.warn("验证码不匹配, target: {}", target);
            return false;
        }

        // 验证成功，删除验证码（一次性使用）
        redisTemplate.delete(key);
        log.info("验证码验证通过, target: {}", target);
        return true;
    }

    /**
     * 获取验证码类型
     */
    @Override
    public String getCaptchaType() {
        return "email";
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成6位数字验证码
     * <p>使用 SecureRandom 保证密码学安全的随机性，防止预测攻击。
     */
    private String generateCaptcha() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 检查发送频率限制（防止恶意刷邮件）
     * <p>使用原子递增+判断的方式，避免 get-then-check 的竞态条件。
     * 如果递增后超过限制，递减回去并抛出异常。
     */
    private void checkSendLimit(String target) {
        String limitKey = LIMIT_KEY + target;
        Long count = redisTemplate.opsForValue().increment(limitKey);

        if (count != null && count == 1) {
            // 第一次发送，设置1小时过期
            redisTemplate.expire(limitKey, 1, TimeUnit.HOURS);
        }

        if (count != null && count > MAX_SEND_PER_HOUR) {
            // 超过限制，递减回去
            redisTemplate.opsForValue().decrement(limitKey);
            log.warn("邮箱验证码发送超限, target: {}, count: {}", target, count);
            throw new BusinessException(ErrorCode.CAPTCHA_SEND_LIMIT,
                    "发送太频繁，请1小时后再试");
        }
    }
}
