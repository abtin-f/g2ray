package com.abtin.tglass.data.td

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * A map whose entries are observed one by one.
 *
 * Compose's `mutableStateMapOf` is a single snapshot object: reading any key in a composable subscribes it to the
 * whole map, so one TDLib update (a file finished downloading, a user went online, a download progressed) would
 * recompose every avatar / row / bubble that read *any* key. Here every key owns a [MutableState] (structural
 * equality, so writing an equal value is free): readers of other keys are not touched at all. Whole-map readers
 * ([values], [asReadOnlyMap]) additionally follow the set of present keys.
 *
 * Main thread only (like all repository state).
 */
internal class KeyedStateMap<K : Any, V : Any> {
    private val states = HashMap<K, MutableState<V?>>()
    private val structure = mutableIntStateOf(0)

    private fun stateOf(key: K): MutableState<V?> = states.getOrPut(key) { mutableStateOf(null) }

    operator fun get(key: K): V? = stateOf(key).value

    operator fun set(key: K, value: V) {
        val st = stateOf(key)
        if (st.value == null) structure.intValue++
        st.value = value
    }

    fun remove(key: K): V? {
        val st = states[key] ?: return null
        val old = st.value
        if (old != null) {
            st.value = null
            structure.intValue++
        }
        return old
    }

    fun containsKey(key: K): Boolean = get(key) != null

    fun clear() {
        states.values.forEach { it.value = null }
        structure.intValue++
    }

    /** Present values (subscribes to the key set and to every value). */
    val values: List<V>
        get() {
            structure.intValue
            return states.values.mapNotNull { it.value }
        }

    /** A read-only [Map] view: `get` is per key, iteration follows everything. */
    fun asReadOnlyMap(): Map<K, V> = object : AbstractMap<K, V>() {
        override val entries: Set<Map.Entry<K, V>>
            get() {
                structure.intValue
                return states.entries.mapNotNull { e -> e.value.value?.let { v -> java.util.AbstractMap.SimpleImmutableEntry(e.key, v) } }.toSet()
            }

        override fun get(key: K): V? = this@KeyedStateMap[key]
        override fun containsKey(key: K): Boolean = this@KeyedStateMap.containsKey(key)
    }
}
