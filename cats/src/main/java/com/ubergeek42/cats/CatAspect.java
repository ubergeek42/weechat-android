package com.ubergeek42.cats;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@Aspect
public class CatAspect {
    @Pointcut("@annotation(cat)")
    public void atCatAnnotation(Cat cat) {}

    @Pointcut("@annotation(catd)")
    public void atCatDAnnotation(CatD catd) {}

    @Pointcut("execution(!synthetic * *(..))")
    public void atMethod() {}

    @Pointcut("execution(!synthetic *.new(..))")
    public void atNew() {}

    @Around("atCatAnnotation(cat) && (atMethod() || atNew())")
    public Object aroundCat(ProceedingJoinPoint point, Cat cat) throws Throwable {
        return around(point, VERBOSE, cat);
    }

    @Around("atCatDAnnotation(catd) && (atMethod() || atNew())")
    public Object aroundCatD(ProceedingJoinPoint point, CatD catd) throws Throwable {
        return around(point, DEBUG, catd);
    }

    private Object around(ProceedingJoinPoint point, int level, Object annotation) throws Throwable {
        Kitty kitty = Cats.getKitty(point);
        if (!kitty.enabled) return point.proceed();
        Cats.CatInfo cat = Cats.getCatInfo(annotation);
        if (!"".equals(cat.tag)) kitty = kitty.kid(cat.tag);
        if (!kitty.enabled) return point.proceed();

        CodeSignature signature = (CodeSignature) point.getSignature();
        String methodName = Utils.getMethodName(signature);

        // log method entering information
        String[] parameterNames = signature.getParameterNames();
        Object[] parameterValues = point.getArgs();
        StringBuilder builder = new StringBuilder("→ ").append(methodName).append('(');
        boolean needComma = false;
        for (int i = 0; i < parameterValues.length; i++) {
            if (i == 0 && parameterNames[i].startsWith("this$")) continue;  // skip first element for inner classes
            if (needComma) builder.append(", ");
            builder.append(parameterNames[i]).append('=');
            builder.append(Strings.toString(parameterValues[i]));
            needComma = true;
        }
        builder.append(')');
        kitty.aspectLog(level, cat.linger, methodName, builder);

        // run the method
        long startNanos = System.nanoTime();
        Object result = point.proceed();
        long stopNanos = System.nanoTime();

        if (!cat.exit) return result;

        // log method exit information, if needed
        long lengthMillis = NANOSECONDS.toMillis(stopNanos - startNanos);
        boolean hasReturnType = signature instanceof MethodSignature
                && ((MethodSignature) signature).getReturnType() != void.class;

        builder = new StringBuilder("← ");
        synchronized (Kitty.class) {
            if (!kitty.lingerieIsValid(level, methodName)) builder.append(methodName).append(" ");
            builder.append("[").append(lengthMillis).append("ms]");
            if (hasReturnType) builder.append(" ⇒ ").append(Strings.toString(result));
            kitty.aspectLog(level, false, methodName, builder);
        }
        return result;
    }
}
