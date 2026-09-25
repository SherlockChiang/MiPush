package one.yufz.hmspush.hook.systemui

import android.content.ComponentName
import android.content.ContextWrapper
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.hook
import java.lang.reflect.Method

class HookSystemUIPlugin(
    private val pluginPackageName: String, private val hooker: ISystemUIPluginHooker
) {
    companion object {
        private const val TAG = "HookSystemUIPlugin"
        private val COMPONENT_FIELD_NAMES = arrayOf(
            "mComponentName",
            "componentName",
            "mPluginComponentName"
        )
        private val COMPONENT_METHOD_NAMES = arrayOf(
            "getComponentName",
            "getPluginComponentName"
        )
    }

    fun hook(classLoader: ClassLoader) {
        try {
            val classPluginFactory = XposedHelpers.findClass(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
                classLoader
            )
            classPluginFactory.declaredMethods.find { it.name == "createPluginContext" }!!.hook {
                doAfter {
                    val packageName = resolvePluginPackageName(thisObject, result)
                    if (packageName == null) {
                        // Some HyperOS releases removed the component field. Ignore this
                        // invocation and leave the hook installed for a later compatible one.
                        return@doAfter
                    }
                    if (packageName == pluginPackageName) {
                        val pluginLoader = (result as? ContextWrapper)?.classLoader
                        if (pluginLoader == null) {
                            XLog.d(TAG, "skip [$pluginPackageName]: createPluginContext returned ${result?.javaClass?.name}")
                            return@doAfter
                        }
                        unhook()
                        XLog.d(TAG, "hook [$pluginPackageName] by Plugin ClassLoader: [$pluginLoader]")
                        hooker.hook(pluginLoader)
                    }
                }
            }
        } catch (e: Throwable) {
            XLog.e(
                TAG,
                "hook SystemUI Plugin [$pluginPackageName] with [${hooker.javaClass.name}] failure: " + e.message,
                e
            )
        }
    }

    private fun resolvePluginPackageName(factory: Any, result: Any?): String? {
        readComponentName(factory)?.let { return it.packageName }

        val pluginContext = result as? ContextWrapper ?: return null
        val contextPackageName = runCatching { pluginContext.packageName }.getOrNull()
        if (contextPackageName == pluginPackageName) {
            return contextPackageName
        }
        val applicationPackageName = runCatching {
            pluginContext.applicationInfo?.packageName
        }.getOrNull()
        return applicationPackageName?.takeIf { it == pluginPackageName }
    }

    private fun readComponentName(factory: Any): ComponentName? {
        val factoryClass = factory.javaClass
        for (fieldName in COMPONENT_FIELD_NAMES) {
            val field = runCatching {
                XposedHelpers.findFieldIfExists(factoryClass, fieldName)
            }.getOrNull() ?: continue
            val value = runCatching { field.get(factory) }.getOrNull()
            if (value is ComponentName) {
                return value
            }
        }

        for (methodName in COMPONENT_METHOD_NAMES) {
            val method = findComponentMethod(factoryClass, methodName) ?: continue
            val value = runCatching { method.invoke(factory) }.getOrNull()
            if (value is ComponentName) {
                return value
            }
        }
        return null
    }

    private fun findComponentMethod(clazz: Class<*>, methodName: String): Method? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            val method = currentClass.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty() &&
                    ComponentName::class.java.isAssignableFrom(it.returnType)
            }
            if (method != null) {
                runCatching { method.isAccessible = true }
                return method
            }
            currentClass = currentClass.superclass
        }
        return null
    }

}
