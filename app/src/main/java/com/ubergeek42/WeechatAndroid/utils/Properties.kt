package com.ubergeek42.WeechatAndroid.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty


object Missing


abstract class InvalidatableLazyProperty<T>(
    protected var initializer: (() -> T)
) : ReadOnlyProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    @Volatile
    protected var value: T = Missing as T

    fun invalidate() {
        @Suppress("UNCHECKED_CAST")
        value = Missing as T
    }

    fun getValueOrNull() = value.let { if (it === Missing) null else it }
}


class SynchronizedInvalidatableLazyProperty<T>(
    initializer: (() -> T)
) : InvalidatableLazyProperty<T>(initializer) {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        var value = this.value

        if (value === Missing) {
            synchronized(this) {
                value = this.value
                if (value === Missing) {
                    value = initializer()
                    this.value = value
                }
            }
        }

        return value
    }
}


fun <T> invalidatableLazy(initializer: () -> T) = SynchronizedInvalidatableLazyProperty(initializer)


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


class ResettableState<T>(
    private val initialValue: T
): ReadWriteProperty<Any?, T> {
    private var value = initialValue

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { this.value = value }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    fun reset() { value = initialValue }
}


class ResettableStateManager {
    private val states = mutableListOf<ResettableState<*>>()

    fun resetAll() = states.forEach { it.reset() }

    operator fun <T> invoke(block: () -> T) = ResettableState(block()).also { states.add(it) }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


class UpdatableGettableProperty<V>(
    private val valueGetter: () -> V,
    private val flagSetter: KMutableProperty0<Boolean>
) : ReadWriteProperty<Any?, V> {
    @Suppress("UNCHECKED_CAST") var newValue: V = Unit as V

    override fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return if (newValue === Unit) valueGetter() else newValue
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        newValue = value
        flagSetter.set(true)
    }
}


class UpdatableProperty<V>(
    private val flagSetter: KMutableProperty0<Boolean>
) : ReadWriteProperty<Any?, V> {
    @Suppress("UNCHECKED_CAST") var newValue: V = Unit as V

    override fun getValue(thisRef: Any?, property: KProperty<*>): V {
        if (newValue === Unit) throw NotImplementedError("Can't get property ${property.name}")
        return newValue
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        newValue = value
        flagSetter.set(true)
    }
}

inline fun <V> updatable(flagSetter: KMutableProperty0<Boolean>, noinline valueGetter: () -> V) =
        UpdatableGettableProperty(valueGetter, flagSetter)
inline fun <V> updatable(flagSetter: KMutableProperty0<Boolean>) =
        UpdatableProperty<V>(flagSetter)