package com.lagradost.cloudstream3.utils

open class Event<T> {
    private val observers = mutableListOf<(T) -> Unit>()

    operator fun plusAssign(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    fun invoke(value: T) {
        val list = synchronized(observers) { observers.toList() }
        list.forEach { runCatching { it.invoke(value) } }
    }
}
