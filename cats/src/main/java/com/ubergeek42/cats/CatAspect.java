package com.ubergeek42.cats;

import android.support.annotation.NonNull;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Field;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@SuppressWarnings("unused")
@Aspect
public class CatAspect {
    @Pointcut("@annotation(cat)")
    public void atCatAnnotation(Cat cat) {
    }

    @Pointcut("@annotation(catd)")
    public void atCatDAnnotation(CatD catd) {
    }

    @Pointcut("execution(!synthetic * *(..))")
    public void atMethod() {
    }

    @Pointcut("execution(!synthetic *.new(..))")
    public void atNew() {
    }

    @Around("atCatAnnotation(cat) && (atMethod() || atNew())")
    public Object aroundCat(ProceedingJoinPoint point, Cat cat) throws Throwable {
        return around(point, cat.value(), cat.linger(), cat.exit(), VERBOSE);
    }

    @Around("atCatDAnnotation(catd) && (atMethod() || atNew())")
    public Object aroundCatD(ProceedingJoinPoint point, CatD catd) throws Throwable {
        return around(point, catd.value(), catd.linger(), catd.exit(), DEBUG);
    }

    private Object around(ProceedingJoinPoint point, String tag,
                          boolean linger, boolean exit, int level) throws Throwable {
        Kitty kitty = getKitty(point);
        if (!"".equals(tag)) kitty = kitty.kid(tag);
        if (!kitty.enabled) return point.proceed();

        linger = linger || exit;

        CodeSignature signature = (CodeSignature) point.getSignature();
        String methodName = Utils.getMethodName(signature);

        // log method entering information
        String[] parameterNames = signature.getParameterNames();
        Object[] parameterValues = point.getArgs();
        StringBuilder builder = new StringBuilder("→ ").append(methodName).append('(');
        // skip first element for inner classes
        int i = parameterNames.length > 0 && "this$0".equals(parameterNames[0]) ? 1 : 0;
        for (boolean needComma = false; i < parameterValues.length; i++) {
            if (needComma) builder.append(", ");
            builder.append(parameterNames[i]).append('=');
            builder.append(Strings.toString(parameterValues[i]));
            needComma = true;
        }
        builder.append(')');
        kitty.aspectLog(level, linger, methodName, builder);

        // run the method
        long startNanos = System.nanoTime();
        Object result = point.proceed();
        long stopNanos = System.nanoTime();

        if (!exit) return result;

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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get kitty for current joint point
    // kitty can be public or private, static or non-static
    // kitty can be a field of current class as well as of enclosing classes

    // public class TopLevel {
    //     static class Static {                        // outer = null
    //         class NonStaticOuter {                   // outer = this$0
    //             class NonStaticInner {               // outer = this$1
    //                 class NonStaticInnerInner {      // outer = this$2

    // the full name of the latter would be:
    // package.TopLevel$Static$NonStaticOuter$NonStaticInner$NonStaticInnerInner
    private static @NonNull Kitty getKitty(JoinPoint point) {
        Object object = point.getThis();
        Class cls = point.getSignature().getDeclaringType();
        int depth = -100;
        while (cls != null) {
            // try returning local kitty
            // object might be null for static stuff
            Kitty kitty = (Kitty) getField(cls, object, "kitty");
            if (kitty != null) return kitty;

            // try getting the instance of immediate outer class, like Outer.this
            // if there's no outer class or outer class is static, use null
            if (object != null) {
                Object outer = null;
                if (depth == -100) depth = Utils.countCharacters(cls.getName(), '$') - 1;
                while (depth >= 0 && outer == null) outer = getField(cls, object, "this$" + depth--);
                object = outer;
            }
            cls = cls.getEnclosingClass();
        }
        throw new RuntimeException("kitty not found for point: " + point);
    }

    // get field for class and object, returns null if not found
    // if the field is declared and static, object is not used, may be null
    private static Object getField(Class cls, Object o, String name) {
        //System.out.println("looking in `" + cls + "` `" + o + "` for `" + name + "`");
        do {
            try {
                Field field = cls.getDeclaredField(name);
                if (!field.isAccessible()) field.setAccessible(true);
                return field.get(o);
            } catch (Exception e) {
                cls = cls.getSuperclass();
            }
        } while (cls != null);
        return null;
    }
}