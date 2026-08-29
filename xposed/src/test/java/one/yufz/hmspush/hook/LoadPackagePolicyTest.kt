package one.yufz.hmspush.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadPackagePolicyTest {
    @Test
    fun androidPackageInAndroidProcessInstallsSystemHook() {
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_HOOK,
            LoadPackagePolicy.route("android", "android", "android", 1000),
        )
    }

    @Test
    fun androidPackageInSystemServerInstallsSystemHook() {
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_HOOK,
            LoadPackagePolicy.route("android", "system_server", "system_server", 1000),
        )
    }

    @Test
    fun providerCallbackInSystemServerNeverRunsAppFakes() {
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_SKIP,
            LoadPackagePolicy.route(
                "com.android.providers.settings",
                null,
                "system_server",
                1000,
            ),
        )
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_SKIP,
            LoadPackagePolicy.route(
                "com.android.server.telecom",
                null,
                null,
                1000,
            ),
        )
    }

    @Test
    fun canonicalAndroidPackageUsesSystemHookWithMissingProcessProbes() {
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_HOOK,
            LoadPackagePolicy.route("android", null, null, 1000),
        )
    }

    @Test
    fun explicitAppPackageCannotOverrideKnownSystemHost() {
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_SKIP,
            LoadPackagePolicy.route(
                "com.xiaomi.xmsf",
                "system_server",
                "system_server",
                1000,
            ),
        )
    }

    @Test
    fun regularAppStillUsesAppRoute() {
        assertEquals(
            LoadPackagePolicy.Route.APP,
            LoadPackagePolicy.route(
                "com.example.client",
                "com.example.client",
                "com.example.client",
                10_000,
            ),
        )
    }

    @Test
    fun systemUiAndXmsfRemainAppHostedRoutes() {
        assertEquals(
            LoadPackagePolicy.Route.APP,
            LoadPackagePolicy.route(
                "com.android.systemui",
                "com.android.systemui",
                "com.android.systemui",
                1000,
            ),
        )
        assertEquals(
            LoadPackagePolicy.Route.APP,
            LoadPackagePolicy.route(
                "com.xiaomi.xmsf",
                "com.xiaomi.xmsf",
                "com.xiaomi.xmsf",
                1000,
            ),
        )
    }

    @Test
    fun processNameAloneCannotClaimSystemHost() {
        assertEquals(
            LoadPackagePolicy.Route.APP,
            LoadPackagePolicy.route(
                "com.example.client",
                "system_server",
                "system_server",
                10_000,
            ),
        )
    }

    @Test
    fun explicitSystemPackagesSurviveUnknownHostFallback() {
        assertEquals(
            LoadPackagePolicy.Route.APP,
            LoadPackagePolicy.route("com.android.systemui", null, null, 1000),
        )
        assertEquals(
            LoadPackagePolicy.Route.APP,
            LoadPackagePolicy.route("com.xiaomi.xmsf", null, null, 1000),
        )
    }

    @Test
    fun androidPackageNeverFallsIntoAppRouteWhenHostProbeIsMissing() {
        assertEquals(
            LoadPackagePolicy.Route.SYSTEM_SKIP,
            LoadPackagePolicy.route("android", null, "com.android.systemui", 1000),
        )
    }
}
