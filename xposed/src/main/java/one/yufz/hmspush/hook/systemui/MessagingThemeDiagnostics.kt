package one.yufz.hmspush.hook.systemui

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import java.util.ArrayDeque

/** Bounded, content-free observations of the frame actually presented by SystemUI. */
internal class MessagingThemeDiagnostics {
    private var remainingFrames = 6

    fun observe(row: View, eligible: () -> Boolean) {
        if (remainingFrames <= 0 ||
            XposedHelpers.getAdditionalInstanceField(row, PENDING_FIELD) == true) return
        val pending = PendingFrame(row) {
            if (remainingFrames > 0 && eligible()) {
                remainingFrames--
                dump(row)
            }
        }
        XposedHelpers.setAdditionalInstanceField(row, PENDING_FIELD, true)
        pending.start()
    }

    private class PendingFrame(val row: View, val capture: () -> Unit) :
        ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private var observer = row.viewTreeObserver

        fun start() {
            observer.addOnPreDrawListener(this)
            row.addOnAttachStateChangeListener(this)
        }

        override fun onPreDraw(): Boolean {
            remove()
            runCatching(capture).onFailure { XLog.e(TAG, "frame diagnostic unavailable", it) }
            return true
        }

        override fun onViewAttachedToWindow(view: View) {
            if (observer.isAlive) observer.removeOnPreDrawListener(this)
            observer = row.viewTreeObserver
            observer.addOnPreDrawListener(this)
        }

        override fun onViewDetachedFromWindow(view: View) = remove()

        private fun remove() {
            if (observer.isAlive) observer.removeOnPreDrawListener(this)
            row.removeOnAttachStateChangeListener(this)
            XposedHelpers.removeAdditionalInstanceField(row, PENDING_FIELD)
        }
    }

    private fun dump(row: View) {
        // The traversal is bounded and only happens for six heads-up frames per
        // SystemUI process. No timers, screenshots, names or message text.
        val pending = ArrayDeque<Pair<View, Int>>()
        pending.add(row to -1)
        var visited = 0
        val lines = mutableListOf<String>()
        while (pending.isNotEmpty() && visited < MAX_VIEWS) {
            val (view, parent) = pending.removeFirst()
            val index = visited++
            val id = runCatching { view.resources.getResourceEntryName(view.id) }
                .getOrDefault("none")
            // MessagingLinearLayout hides history through LayoutParams.hide,
            // which is independent of View.visibility/isShown.
            val hiddenByLayout = view.layoutParams?.let { params ->
                runCatching { XposedHelpers.getBooleanField(params, "hide") }.getOrNull()
            }
            val base = "$index parent=$parent ${view.javaClass.simpleName}/$id" +
                    " visible=${view.visibility} shown=${view.isShown} alpha=${view.alpha}" +
                    " size=${view.width}x${view.height} layer=${view.layerType}" +
                    " hiddenByLayout=$hiddenByLayout"
            if (view is TextView) {
                val text = view.text
                val spans = if (text is Spanned) {
                    val foreground = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                        .take(4).map { "fg=${hex(it.foregroundColor)}" }
                    val appearance = text.getSpans(0, text.length, TextAppearanceSpan::class.java)
                        .take(4).map { "appearance=${it.textColor?.defaultColor?.let(::hex)}" }
                    foreground + appearance
                } else emptyList()
                lines += "$base empty=${text.isEmpty()} color=${hex(view.currentTextColor)}" +
                        " default=${hex(view.textColors.defaultColor)} paint=${hex(view.paint.color)}" +
                        " night=${view.resources.configuration.uiMode and 0x30} spans=$spans"
            } else if (view is ViewGroup) {
                lines += base
            }
            if (view is ViewGroup) {
                for (child in 0 until minOf(view.childCount, MAX_VIEWS)) {
                    if (pending.size + visited >= MAX_VIEWS) break
                    pending.add(view.getChildAt(child) to index)
                }
            }
        }
        // Keep each log entry below Android's per-entry payload limit.
        val frame = "row=${System.identityHashCode(row)} remaining=$remainingFrames"
        XLog.d(TAG, "heads-up pre-draw $frame views=$visited")
        lines.chunked(4).forEachIndexed { part, chunk ->
            XLog.d(TAG, "$frame part=$part\n" + chunk.joinToString("\n"))
        }
    }

    private fun hex(color: Int) = "0x${color.toUInt().toString(16)}"

    companion object {
        private const val TAG = "MessagingThemeFrame"
        private const val PENDING_FIELD = "one.yufz.hmspush.messagingThemeFramePending"
        private const val MAX_VIEWS = 160
    }
}
