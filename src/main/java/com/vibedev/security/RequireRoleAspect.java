package com.vibedev.security;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class RequireRoleAspect {

    private static final Logger log = LoggerFactory.getLogger(RequireRoleAspect.class);

    @Before("@within(requireRole) || @annotation(requireRole)")
    public void checkRole(JoinPoint joinPoint, RequireRole requireRole) {
        // Find Authentication in method arguments
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof Authentication auth) {
                String role = SecurityHelper.getRole(auth);
                List<String> allowed = Arrays.asList(requireRole.value());
                if (!allowed.contains(role)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN,
                            "需要 " + String.join("/", allowed) + " 权限");
                }
                return;
            }
        }
        log.warn("@RequireRole on method with no Authentication parameter: {}",
                joinPoint.getSignature().toShortString());
    }
}
