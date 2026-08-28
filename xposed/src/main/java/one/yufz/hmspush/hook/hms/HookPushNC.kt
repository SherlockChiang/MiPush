package one.yufz.hmspush.hook.hms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import one.yufz.hmspush.hook.XLog
import one.yufz.hmspush.hook.hms.nm.SystemNotificationManager
import one.yufz.hmspush.hook.system.HookSystemService
import one.yufz.xposed.findClass
import one.yufz.xposed.hookMethod
import one.yufz.xposed.set

/** Hooks the XMSF notification facade and routes it through the Binder bridge. */
object HookPushNC {
    private const val TAG = "HookPushNC"
    private const val TARGET_CLASS = "com.nihility.notification.NotificationManagerEx"

    private val hookCheck = { HookSystemService.isSystemHookReady }

    fun canHook(classLoader: ClassLoader): Boolean {
        return try {
            classLoader.findClass(TARGET_CLASS)
            true
        } catch (_: ClassNotFoundError) {
            false
        }
    }

    fun hook(classLoader: ClassLoader) {
        XLog.d(TAG, "hookPushNC() called with: classLoader = $classLoader")

        val classNotificationManager = try {
            classLoader.findClass(TARGET_CLASS)
        } catch (error: Throwable) {
            XLog.e(TAG, "notification facade is unavailable", error)
            return
        }

        var notifyHooked = false
        tryInstall("notify") {
            classNotificationManager.hookMethod(
                "notify",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Notification::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.notify(
                            args[0] as String,
                            args[1] as String?,
                            args[2] as Int,
                            args[3] as Notification,
                        )
                        null
                    }
                }
            }
        }.also { notifyHooked = it }

        // The flag is consumed by the framework UI to decide whether target
        // package channels can be queried. Set it only after the facade hook
        // was actually installed.
        if (notifyHooked) {
            try {
                classNotificationManager["isHooked"] = true
            } catch (error: Throwable) {
                XLog.e(TAG, "unable to set isHooked", error)
            }
        }

        tryInstall("cancel") {
            classNotificationManager.hookMethod(
                "cancel",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.cancel(
                            args[0] as String,
                            args[1] as String?,
                            args[2] as Int,
                        )
                        null
                    }
                }
            }
        }

        tryInstall("createNotificationChannels") {
            classNotificationManager.hookMethod(
                "createNotificationChannels",
                String::class.java,
                List::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.createNotificationChannels(
                            args[0] as String,
                            args[1] as List<NotificationChannel>,
                        )
                        null
                    }
                }
            }
        }

        tryInstall("getNotificationChannel") {
            classNotificationManager.hookMethod(
                "getNotificationChannel",
                String::class.java,
                String::class.java,
            ) {
                // This method used to be unconditionally replaced. If the
                // hidden service is unavailable that made every channel query
                // throw and could prevent XMSF from starting.
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.getNotificationChannel(
                            args[0] as String,
                            args[1] as String?,
                        )
                    }
                }
            }
        }

        tryInstall("getNotificationChannels") {
            classNotificationManager.hookMethod(
                "getNotificationChannels",
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.getNotificationChannels(args[0] as String)
                    }
                }
            }
        }

        tryInstall("deleteNotificationChannel") {
            classNotificationManager.hookMethod(
                "deleteNotificationChannel",
                String::class.java,
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.deleteNotificationChannel(
                            args[0] as String,
                            args[1] as String,
                        )
                        null
                    }
                }
            }
        }

        tryInstall("createNotificationChannelGroups") {
            classNotificationManager.hookMethod(
                "createNotificationChannelGroups",
                String::class.java,
                List::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.createNotificationChannelGroups(
                            args[0] as String,
                            args[1] as List<NotificationChannelGroup>,
                        )
                        null
                    }
                }
            }
        }

        tryInstall("getNotificationChannelGroup") {
            classNotificationManager.hookMethod(
                "getNotificationChannelGroup",
                String::class.java,
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.getNotificationChannelGroup(
                            args[0] as String,
                            args[1] as String,
                        )
                    }
                }
            }
        }

        tryInstall("getNotificationChannelGroups") {
            classNotificationManager.hookMethod(
                "getNotificationChannelGroups",
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.getNotificationChannelGroups(args[0] as String)
                    }
                }
            }
        }

        tryInstall("deleteNotificationChannelGroup") {
            classNotificationManager.hookMethod(
                "deleteNotificationChannelGroup",
                String::class.java,
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.deleteNotificationChannelGroup(
                            args[0] as String,
                            args[1] as String,
                        )
                        null
                    }
                }
            }
        }

        tryInstall("areNotificationsEnabled") {
            classNotificationManager.hookMethod(
                "areNotificationsEnabled",
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.areNotificationsEnabled(args[0] as String)
                    }
                }
            }
        }

        tryInstall("getActiveNotifications") {
            classNotificationManager.hookMethod(
                "getActiveNotifications",
                String::class.java,
            ) {
                replace(hookCheck) {
                    bridgeOrOriginal(this) {
                        SystemNotificationManager.getActiveNotifications(args[0] as String)
                    }
                }
            }
        }
    }

    private fun tryInstall(label: String, install: () -> Unit): Boolean {
        return try {
            install()
            XLog.d(TAG, "hooked $label")
            true
        } catch (error: Throwable) {
            XLog.e(TAG, "unable to hook $label", error)
            false
        }
    }

    /**
     * A bridge call is an optimization/compatibility layer. If a vendor API,
     * permission hook, or reflection signature is unavailable, invoke the
     * original facade method so the normal XMSF notification path still runs.
     */
    private inline fun bridgeOrOriginal(
        param: de.robv.android.xposed.XC_MethodHook.MethodHookParam,
        bridge: () -> Any?,
    ): Any? {
        return try {
            bridge()
        } catch (error: Throwable) {
            XLog.e(TAG, "notification bridge failed; falling back to original", error)
            try {
                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
            } catch (originalError: Throwable) {
                XLog.e(TAG, "original notification method also failed", originalError)
                throw originalError
            }
        }
    }
}
