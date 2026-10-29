package app.haven.launcher.system

import android.content.Context

/** Which kind of TV box Haven is on, for the few places where Google TV, Fire TV and the DIRECTV box differ. */
enum class Device(val label: String) {
    Directv("DIRECTV box"),
    FireTv("Fire TV"),
    AndroidTv("Android TV");

    companion object {
        fun of(ctx: Context): Device {
            val pm = ctx.packageManager
            return when {
                pm.hasSystemFeature("amazon.hardware.fire_tv") -> FireTv
                HomeButtonService.stockHomePackages(ctx).any { it.contains("directv", ignoreCase = true) } -> Directv
                else -> AndroidTv
            }
        }
    }
}
