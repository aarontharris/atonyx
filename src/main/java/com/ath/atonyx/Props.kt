package com.ath.atonyx

import java.util.*


class Delta<T>(val src: PropChangeSource, val cur: T, val prv: T?, val usr: T, val sys: T) {
    fun describe(): String = "$src triggered '$prv' to '$cur' -- cur='$cur', sys='$sys', usr='$usr'"
}

/** @param onChangePropagated is called only if/when a change is propagated to [ManagedProps.cur] */
open class Prop<T>(
    val props: ManagedProps,
    val default: T,
    val asap: Boolean = true,
    val onChangePropagated: ((Delta<T>) -> Unit)? = null
) {

    val cur: T
        get() = props.cur[this] ?: default

    var sys: T?
        get() = (props.sys)[this]
        set(value) {
            (props.sys)[this] = value
        }

    var usr: T?
        get() = (props.usr)[this]
        set(value) {
            (props.usr)[this] = value
        }

}

///** @param work is called only if/when a change is propagated to [ManagedProps.cur] */
//class PropN<T>(props: ManagedProps, default: T, work: ((Delta<T>) -> Unit)? = null) :
//    Prop<T>(props, default, true, work)

///** @param work is called only if/when a change is propagated to [ManagedProps.cur] */
//class PropQ<T>(props: ManagedProps, default: T, work: ((Delta<T>) -> Unit)? = null) :
//    Prop<T>(props, default, false, work)

enum class PropChangeSource {
    CUR,
    SYS,
    USR
}
/**
 *
 * # TLDR;
 *
 * Register a callback to handle a property change observed between system state and user state.
 * A change only occurs when the user requests it, or the system overrides or ceases to override.
 *
 * # MORE;
 *
 * Given a set of:
 * - User Properties -- State desired by the User
 * - System Properties -- State overriden by the System
 * - Current Properties -- State as it is now
 * Where precedence: current = system ?: user
 * Such that the system is considered an override to the user's desired state.
 *
 * This Class allows you to register handlers to be invoked when "current" changes.
 * "Current" is only changed when
 *  - The user deliberately changes state
 *  - The system chooses to override or cease to override
 *
 * # HOW?
 * You can register a map of handlers by key => handler.
 * props.instant( key ) { old, new -> doItNow( new ) }
 * props.enqueue( key ) { old, new -> doItLater( new ) }
 *
 * instant
 *  - usr|sys change will be reflected in cur immediately and the handler invoked immediately
 * enqueue
 *  - usr|sys change will be recorded now, but cur & handler is queued until [process]
 *
 *  By registering a handler to be instant or enqueue, this becomes the default behavior
 *  which alleviates the end-developer from being concerned about it what must be queued or not.
 *  However, if a need arrises to ensure that a property is being handled NOW
 *  You may: set(key, value).also{ process(key) }
 *
 */
open class ManagedProps {

    internal inner class PropsRW {
        val cache = HashMap<Prop<Any?>, Any?>()
        fun keys(): Set<Prop<Any?>> = cache.keys

        operator fun <T> get(key: Prop<T>): T? {
            synchronized(this@ManagedProps) {
                return cache[key as Prop<Any?>] as T?
            }
        }

        operator fun set(key: Prop<*>, value: Any?) {
            // val reg: Map<Prop<Any?>, (Any?, Any?) -> Unit> = if (key.asap) regAuto else regQueue
            synchronized(this@ManagedProps) {
                cache[key as Prop<Any?>] = value
                val src: PropChangeSource = when (this) {
                    cur -> PropChangeSource.CUR
                    sys -> PropChangeSource.SYS
                    usr -> PropChangeSource.USR
                    else -> TODO("Unimplemented PropChangeSource")
                }
                handleChange(src, key)
            }
        }

    }

    internal val cur = PropsRW()
    internal val usr = PropsRW()
    internal val sys = PropsRW()
    private var insideCallback: Int? = null

    private fun insideCallback(key: Prop<Any?>): Boolean {
        synchronized(this) {
            return insideCallback != null && insideCallback == key.onChangePropagated?.hashCode()
        }
    }

    private fun handleChange(src: PropChangeSource, key: Prop<Any?>) {
        synchronized(this) {
            val p = cur.cache[key] ?: key.default
            val u = usr.cache[key]
            val s = sys.cache[key]
            val c = s ?: u ?: key.default
            if (c != p) {
                cur.cache[key] = c
                if (!insideCallback(key)) {
                    insideCallback = key.onChangePropagated?.hashCode()
                    try {
                        key.onChangePropagated?.invoke(Delta(src, c, p, u, s))
                    } finally {
                        insideCallback = null
                    }
                }
            }
        }
    }
}