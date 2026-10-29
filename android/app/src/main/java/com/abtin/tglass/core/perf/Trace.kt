package com.abtin.tglass.core.perf

/**
 * A named systrace / Perfetto section around [block] in debug builds (`BuildConfig.DEBUG` is a constant, so release
 * builds compile it down to the bare block).
 */
inline fun <T> perfSection(name: String, block: () -> T): T {
    if (!com.abtin.tglass.BuildConfig.DEBUG) return block()
    android.os.Trace.beginSection(name)
    try {
        return block()
    } finally {
        android.os.Trace.endSection()
    }
}
