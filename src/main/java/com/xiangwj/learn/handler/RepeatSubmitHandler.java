package com.xiangwj.learn.handler;

import com.xiangwj.learn.annotation.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.util.DigestUtils;
@Aspect
@Component
public class RepeatSubmitHandler {

    // 使用内存缓存（生产环境建议替换为 Redis）
    private static final ConcurrentHashMap<String, Long> SUBMIT_CACHE = new ConcurrentHashMap<>();

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String cacheKey = generateCacheKey(joinPoint, repeatSubmit.limitType(), request);
        long currentTime = System.currentTimeMillis();

        // 检查是否重复提交
        if (SUBMIT_CACHE.containsKey(cacheKey)) {
            long lastSubmitTime = SUBMIT_CACHE.get(cacheKey);
            if (currentTime - lastSubmitTime < TimeUnit.SECONDS.toMillis(repeatSubmit.logtime())) {
                throw new RuntimeException("请勿重复提交");
            }
        }

        // 记录当前提交
        SUBMIT_CACHE.put(cacheKey, currentTime);

        try {
            return joinPoint.proceed();
        } finally {
            // 可选：提交完成后移除缓存（或依赖过期时间）
            // SUBMIT_CACHE.remove(cacheKey);
        }
    }

    /**
     * 生成缓存 Key（根据限制类型）
     */
    private String generateCacheKey(ProceedingJoinPoint joinPoint, RepeatSubmit.type limitType, HttpServletRequest request) {
        switch (limitType) {
            case PARAM:
                return generateParamKey(joinPoint);
            case TOKEN:
                return generateTokenKey(request);
            default:
                throw new IllegalArgumentException("不支持的防重复提交类型");
        }
    }

    /**
     * 参数模式：类名 + 方法名 + 参数内容（MD5 压缩）
     */
    private String generateParamKey(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        Object[] args = joinPoint.getArgs();

        // 参数序列化（简单示例，可根据需求优化）
        String paramsJson = String.valueOf(args);
        String paramHash = DigestUtils.md5DigestAsHex(paramsJson.getBytes(StandardCharsets.UTF_8));

        return String.format("submit:param:%s:%s:%s", className, methodName, paramHash);
    }

    /**
     * Token 模式：Token + URL
     */
    private String generateTokenKey(HttpServletRequest request) {
        String token = request.getHeader("Authorization"); // 根据实际 Token 位置调整
        String url = request.getRequestURI();
        return String.format("submit:token:%s:%s", token, url);
    }
}
