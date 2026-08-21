package one.yufz.hmspush.hook.fakedevice

import android.os.Build
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.*
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "FakeProperties"

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

fun fakeProperty(vararg properties: Pair<String, String>) {
    propertyMap.putAll(properties)

    if (propertyMap.containsKey(Property.BRAND.key)) {
        Build::class.java["BRAND"] = propertyMap[Property.BRAND.key]
    }

    if (propertyMap.containsKey(Property.MANUFACTURER.key)) {
        Build::class.java["MANUFACTURER"] = propertyMap[Property.MANUFACTURER.key]
    }

    if (propertyMap.containsKey("ro.product.model")) {
        Build::class.java["MODEL"] = propertyMap["ro.product.model"]
    }

    if (propertyMap.containsKey("ro.build.display.id")) {
        Build::class.java["DISPLAY"] = propertyMap["ro.build.display.id"]
    }

    if (propertyMap.containsKey("ro.build.user")) {
        Build::class.java["USER"] = propertyMap["ro.build.user"]
    }

    if (hooked.getAndSet(true)) return

    val classSystemProperties = Build::class.java.classLoader.findClass("android.os.SystemProperties")

    val callback: HookContext.() -> Unit = {
        doBefore {
            val key = args[0] as String
            propertyMap[key]?.let {
                result = it
            }
        }
    }

    classSystemProperties.hookMethod("get", String::class.java, callback = callback)
    classSystemProperties.hookMethod("get", String::class.java, String::class.java, callback = callback)

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
}
