package com.ubergeek42.cats;

import android.support.annotation.NonNull;

import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.ConstructorSignature;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Utils {

    private final static String PACKAGE = "com.ubergeek42.cats";

    private static StackTraceElement getCallerStackTraceElement() {
        StackTraceElement[] e = Thread.currentThread().getStackTrace();
        int i = 0;
        for (; i < e.length; i++) if (e[i].getClassName().startsWith(PACKAGE)) break;       // skip until this package
        for (; i < e.length; i++) if (!e[i].getClassName().startsWith(PACKAGE)) break;      // skip this package
        if (i < e.length) return e[i];
        throw new RuntimeException("can't find caller");
    }

    // returns simple class name for the caller, including the outer class (Foo, Foo$Bar)
    static @NonNull String getCallerClassSimpleName() {
        String fullClassName = getCallerStackTraceElement().getClassName();
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }

    private static String getThrowableAsString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    // similar to String.format, but formats the string using Strings;
    // also appends exception info, if exception is last element
    static String format(String message, Object... args) {
        Object lastArg = args[args.length - 1];
        for (int i = 0; i < args.length; i++) args[i] = Strings.toString(args[i]);
        message = String.format(message, args);
        if (lastArg instanceof Throwable)
            message += "\n" + Utils.getThrowableAsString((Throwable) lastArg);
        return message;
    }

    // retrieves the method (someMethod) or a constructor name (SomeConstructor) for a signature
    static @NonNull String getMethodName(Signature signature) {
        return signature instanceof ConstructorSignature ?
                signature.getDeclaringType().getSimpleName() :
                signature.getName();
    }

    static StringBuilder addAndPad(StringBuilder sb, CharSequence s, int length) {
        if (length < s.length()) return sb.append(s,0, length);
        else if (length == s.length()) return sb.append(s);
        sb.append(s);
        length -= s.length();
        while (length-- > 0) sb.append(" ");
        return sb;
    }
}