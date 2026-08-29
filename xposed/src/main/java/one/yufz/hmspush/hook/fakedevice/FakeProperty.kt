package one.yufz.hmspush.hook.fakedevice

import android.os.Build
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.*
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "FakeProperties"
private const val ANDROID_15_API = 35

enum class Property(val entry: Pair<String, String>) {
    EMUI_API("ro.build.hw_emui_api_level" to ""),
    EMUI_VERSION("ro.build.version.emui" to ""),
    BRAND("ro.product.brand" to "Xiaomi"),
    MANUFACTURER("ro.product.manufacturer" to "Xiaomi"),
    MIUI_VERSION_NAME("ro.miui.ui.version.name" to "V130"),
    MIUI_VERSION_CODE("ro.miui.ui.version.code" to "13"),
    FLYME_VERSION_NAME("ro.build.flyme.version" to ""),
    FLYME_VERSION_CODE("ro.flyme.version.id" to ""),
    COLOROS_BUILD_VERSION_OLD("ro.build.version.opporom" to ""),
    COLOROS_BUILD_VERSION("ro.build.version.oplusrom" to ""),

    REGION_MIUI("ro.miui.region" to "CN"),
    REGION_PRODUCT_LOCALE("ro.product.locale.region" to "CN"),
    REGION_PRODUCT_COUNTRY("ro.product.country.region" to "CN"),
    REGION_PERSIST_COUNTRY("persist.sys.country" to "CN"),
    ;

    val key: String
        get() = entry.first

    val value: String
        get() = entry.second
}


fun fakeProperty(property: Property, overrideValue: String) = fakeProperty(Pair(property.key, overrideValue))

fun fakeAllBuildInProperties() = fakeProperty(*Property.values().map { it.entry }.toTypedArray())

fun fakeProperty(vararg properties: Property) {
    fakeProperty(*properties.map { it.entry }.toTypedArray())
}

private val propertyMap: MutableMap<String, String> = HashMap()
private val hooked = AtomicBoolean(false)
private val buildFieldWriteAvailable = AtomicBoolean(true)
private val propertyHookLock = Any()

fun fakeProperty(vararg properties: Pair<String, String>) {
    propertyMap.putAll(properties)

    installPropertyHooksOnce()

    if (propertyMap.containsKey(Property.BRAND.key)) {
        setBuildFieldSafely("BRAND", propertyMap[Property.BRAND.key])
    }

    if (propertyMap.containsKey(Property.MANUFACTURER.key)) {
        setBuildFieldSafely("MANUFACTURER", propertyMap[Property.MANUFACTURER.key])
    }

    if (propertyMap.containsKey("ro.product.model")) {
        setBuildFieldSafely("MODEL", propertyMap["ro.product.model"])
    }

    if (propertyMap.containsKey("ro.build.display.id")) {
        setBuildFieldSafely("DISPLAY", propertyMap["ro.build.display.id"])
    }

    if (propertyMap.containsKey("ro.build.user")) {
        setBuildFieldSafely("USER", propertyMap["ro.build.user"])
    }
}

private fun installPropertyHooksOnce() {
    if (hooked.get()) return

    synchronized(propertyHookLock) {
        if (hooked.get()) return

        try {
            val classSystemProperties =
                Build::class.java.classLoader.findClass("android.os.SystemProperties")

            val callback: HookContext.() -> Unit = {
                doBefore {
                    val key = args[0] as String
                    propertyMap[key]?.let {
                        result = it
                    }
                }
            }

            var installedAnyHook = false

            try {
                classSystemProperties.hookMethod("get", String::class.java, callback = callback)
                installedAnyHook = true
            } catch (error: Throwable) {
                XLog.d(TAG, "SystemProperties.get hook unavailable: ${error.message}")
            }

            try {
                classSystemProperties.hookMethod(
                    "get",
                    String::class.java,
                    String::class.java,
                    callback = callback,
                )
                installedAnyHook = true
            } catch (error: Throwable) {
                XLog.d(TAG, "SystemProperties.get(default) hook unavailable: ${error.message}")
            }

            try {
                Runtime::class.java.hookMethod("exec", String::class.java) {
                    doBefore {
                        val cmd = args[0] as String
                        if (cmd.startsWith("getprop")) {
                            val key = cmd.removePrefix("getprop").trim()
                            propertyMap[key]?.let {
                                XLog.d(TAG, "hook getprop $key")
                                args[0] = "echo $it"
                            }
                        }
                    }
                }
                installedAnyHook = true
            } catch (error: Throwable) {
                // Runtime.exec is a best-effort shell compatibility hook.
                XLog.d(TAG, "Runtime.exec hook unavailable: ${error.message}")
            }

            // Suppress future attempts once at least one compatibility hook
            // is installed. This prevents duplicate hooks if an optional
            // method is absent on a vendor build.
            if (installedAnyHook) hooked.set(true)
        } catch (error: Throwable) {
            XLog.d(
                TAG,
                "SystemProperties hook unavailable; continue without property override: " +
                    "${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }
}

private fun setBuildFieldSafely(fieldName: String, value: String?) {
    if (!buildFieldWriteAvailable.get()) return

    // ART on Android 15+ rejects reflective writes to these static final
    // fields. SystemProperties hooks provide the supported compatibility
    // path without risking the host process.
    if (Build.VERSION.SDK_INT >= ANDROID_15_API) {
        if (buildFieldWriteAvailable.compareAndSet(true, false)) {
            XLog.d(TAG, "skip Build static-final overrides on API ${Build.VERSION.SDK_INT}")
        }
        return
    }

    try {
        Build::class.java[fieldName] = value
    } catch (error: Throwable) {
        // Some older ART builds may also reject writes to
        // public static final Build fields. SystemProperties hooks remain
        // useful, so degrade this optional compatibility layer gracefully.
        if (buildFieldWriteAvailable.compareAndSet(true, false)) {
            XLog.d(
                TAG,
                "Build.$fieldName override unavailable; keep SystemProperties hooks: " +
                    "${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }
}
