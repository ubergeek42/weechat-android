@file:Suppress("NOTHING_TO_INLINE")

package com.ubergeek42.WeechatAndroid.utils

import android.text.Editable
import android.text.TextWatcher
import android.view.animation.Animation
import android.widget.EditText
import com.bumptech.glide.load.engine.GlideException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


// color = 0xff123456.u -- see https://youtrack.jetbrains.com/issue/KT-4749
val Int.u: Int get() = this
val Long.u: Int get() = toInt()


inline fun <E> MutableList<E>.removeFirst(predicate: (E) -> Boolean) {
    val iterator = this.iterator()
    while (iterator.hasNext()) {
        if (predicate(iterator.next())) {
            iterator.remove()
            return
        }
    }
}

inline fun <E> MutableList<E>.replaceFirstWith(element: E, predicate: (E) -> Boolean) {
    val iterator = this.listIterator()
    while (iterator.hasNext()) {
        if (predicate(iterator.next())) {
            iterator.set(element)
            return
        }
    }
}


fun String.removeChars(badChars: CharSequence): CharSequence {
    return StringBuilder().also { builder ->
        this.forEach { char ->
            if (!badChars.contains(char)) builder.append(char)
        }
    }
}


inline fun EditText.afterTextChanged(crossinline after: (s: Editable) -> Unit) {
    val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) { after(s) }
    }

    addTextChangedListener(textWatcher)
}


inline fun Animation.onAnimationEnd(crossinline block: () -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation?) {}
        override fun onAnimationRepeat(animation: Animation?) {}
        override fun onAnimationEnd(animation: Animation?) {
            block()
        }
    })
}


inline fun <T> List<T>.indexOfOrElse(t: T, default: () -> Int): Int {
    val index = this.indexOf(t)
    return if (index == -1) default() else index
}


inline fun <T> T.isAnyOf(a: T, b: T) = this == a || this == b
inline fun <T> T.isAnyOf(a: T, b: T, c: T) = this == a || this == b || this == c
inline fun <T> T.isAnyOf(a: T, b: T, c: T, d: T) = this == a || this == b || this == c || this == d

inline fun <T> T.isNotAnyOf(a: T, b: T) = !isAnyOf(a, b)
inline fun <T> T.isNotAnyOf(a: T, b: T, c: T) = !isAnyOf(a, b, c)
inline fun <T> T.isNotAnyOf(a: T, b: T, c: T, d: T) = !isAnyOf(a, b, c, d)

////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////

@Suppress("UNCHECKED_CAST")
fun <T : Throwable> Throwable.findCauseInternal(cls: Class<T>): T? {
    return when {
        cls.isInstance(this) -> {
            this as T
        }
        this is GlideException -> {
            rootCauses.forEach { cause ->
                cause.findCauseInternal(cls)?.let { return it }
            }
            null
        }
        else -> {
            cause?.findCauseInternal(cls)
        }
    }
}

inline fun <reified T: Throwable> Throwable.findCause(): T? {
    return findCauseInternal(T::class.java)?.let { return it }
}

inline fun <reified T: Throwable> Throwable.wasCausedBy(): Boolean {
    return findCause<T>() != null
}

inline fun <reified T1: Throwable, reified T2: Throwable> Throwable.wasCausedByEither(): Boolean {
    return findCause<T1>() != null || findCause<T2>() != null
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////// let
////////////////////////////////////////////////////////////////////////////////////////////////////

@OptIn(ExperimentalContracts::class)
inline fun <A> ulet(a: A?, block: (A) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    if (a != null) {
        block(a)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B> ulet(a: A?, b: B?, block: (A, B) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    if (a != null && b != null) {
        block(a, b)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B, R> let(a: A?, b: B?, block: (A, B) -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    return if (a != null && b != null) {
        block(a, b)
    } else {
        null
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B, C> ulet(a: A?, b: B?, c: C?, block: (A, B, C) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    if (a != null && b != null && c != null) {
        block(a, b, c)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B, C, R> let(a: A?, b: B?, c: C?, block: (A, B, C) -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    return if (a != null && b != null && c != null) {
        block(a, b, c)
    } else {
        null
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B, C, D> ulet(a: A?, b: B?, c: C?, d: D?, block: (A, B, C, D) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    if (a != null && b != null && c != null && d != null) {
        block(a, b, c, d)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B, C, D, R> let(a: A?, b: B?, c: C?, d: D?, block: (A, B, C, D) -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    return if (a != null && b != null && c != null && d != null) {
        block(a, b, c, d)
    } else {
        null
    }
}