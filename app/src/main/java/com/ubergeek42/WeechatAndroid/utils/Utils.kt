package com.ubergeek42.WeechatAndroid.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


inline fun EditText.afterTextChanged(crossinline after: (s: Editable) -> Unit) {
    val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) { after(s) }
    }

    addTextChangedListener(textWatcher)
}

inline fun <T> List<T>.indexOfOrElse(t: T, default: () -> Int): Int {
    val index = this.indexOf(t)
    return if (index == -1) default() else index
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