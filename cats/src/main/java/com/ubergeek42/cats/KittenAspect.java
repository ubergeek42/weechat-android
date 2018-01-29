package com.ubergeek42.cats;


import android.support.annotation.NonNull;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import static android.util.Log.ASSERT;
import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;

@SuppressWarnings("unused")
@Aspect
public class KittenAspect {
    @Around("target(Kitty) && (" +
            "call(* trace*(..)) || " +
            "call(* debug*(..)) || " +
            "call(* info*(..)) || " +
            "call(* warn*(..)) || " +
            "call(* error*(..)) || " +
            "call(* wtf*(..))" +
            ")")
    public Object advice(JoinPoint.EnclosingStaticPart staticPart, JoinPoint point) throws Throwable {
        Kitty kitty = (Kitty) point.getTarget();
        if (!kitty.enabled) return null;

        String methodName = Utils.getMethodName(staticPart.getSignature());
        String logMethodName = point.getSignature().getName();
        Object[] parameterValues = point.getArgs();

        String message = (String) parameterValues[0];

        if (parameterValues.length > 1) {
            Object[] args = (Object[]) parameterValues[1];
            if (args.length > 0) message = Utils.format(message, args);
        }

        kitty.aspectLog(getLevel(logMethodName), logMethodName.endsWith("l"), methodName, message);
        return null;
    }

    private static int getLevel(@NonNull String level) {
        switch (level.charAt(0)) {
            case 't': return VERBOSE;
            case 'd': return DEBUG;
            case 'i': return INFO;
            case 'w': return level.charAt(1) == 'a' ? WARN : ASSERT;
            case 'e': return ERROR;
        }
        throw new RuntimeException("can't find level for: " + level);
    }
}
