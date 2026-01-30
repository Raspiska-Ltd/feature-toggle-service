package io.raspiska.featuretoggle.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class FeatureEnabledAspect {

    private final FeatureToggleClient featureToggleClient;

    @Around("@annotation(featureEnabled)")
    public Object checkFeature(ProceedingJoinPoint joinPoint, FeatureEnabled featureEnabled) throws Throwable {
        String featureName = featureEnabled.value();
        String userId = extractUserId(joinPoint, featureEnabled.userIdParam());

        FeatureCheckResult result = featureToggleClient.check(featureName, userId);

        if (!result.isEnabled()) {
            if (featureEnabled.throwOnDisabled()) {
                throw new FeatureDisabledException(featureName, result.getReason());
            }
            log.debug("Feature '{}' is disabled, skipping method execution", featureName);
            return getDefaultReturnValue(joinPoint);
        }

        return joinPoint.proceed();
    }

    private String extractUserId(ProceedingJoinPoint joinPoint, String userIdParam) {
        if (userIdParam == null || userIdParam.isBlank()) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(userIdParam)) {
                Object arg = args[i];
                return arg != null ? arg.toString() : null;
            }
        }

        return null;
    }

    private Object getDefaultReturnValue(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();

        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) return false;
            if (returnType == int.class || returnType == long.class ||
                returnType == short.class || returnType == byte.class) return 0;
            if (returnType == float.class || returnType == double.class) return 0.0;
            if (returnType == char.class) return '\0';
        }
        return null;
    }
}
