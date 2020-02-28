package com.ubergeek42.WeechatAndroid.utils;

import java.util.Collection;

public class Assert {
    public static A assertThat(Object o) {
        return new A(o);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class A {
        final Object a;

        A(Object a) {
            this.a = a;
        }

        private void fail(String message) {
            throw new AssertionError(message + " (Actual: " + a + ")");
        }

        @SuppressWarnings("EqualsReplaceableByObjectsCall")
        private boolean eq(Object b) {
            return  (a == b) || (a != null && a.equals(b));
        }

        ////////////////////////////////////////////////////////////////////////////////////////////

        public void isNull() {
            if (a != null) fail("Actual must be null");
        }

        public void isNotNull() {
            if (a == null) fail("Actual must not be null");
        }

        public void isTrue() {
            if (!eq(true)) fail("Actual must be true");
        }

        public void isFalse() {
            if (!eq(false)) fail("Actual must be false");
        }

        public A isEqualTo(Object b) {
            if (!eq(b)) fail("Actual must be equal to " + b);
            return this;
        }

        public A isNotEqualTo(Object b) {
            if (eq(b)) fail("Actual must not be equal to " + b);
            return this;
        }

        public A contains(Object b) {
            if (!((Collection) a).contains(b)) fail("Actual must contain " + b);
            return this;
        }

        public A doesNotContain(Object b) {
            if (((Collection) a).contains(b)) fail("Actual must not contain " + b);
            return this;
        }

        public A isGreaterThan(int b) {
            if (((int) a) <= b) fail("Actual must be greater than " + b);
            return this;
        }

        public A isLessThanOrEqualTo(int b) {
            if (((int) a) > b) fail("Actual must be less than " + b);
            return this;
        }
    }
}
