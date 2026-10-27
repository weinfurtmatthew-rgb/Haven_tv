package app.haven.launcher.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.haven.launcher.BuildConfig
import app.haven.launcher.MainActivity
import app.haven.launcher.R
import androidx.core.content.res.ResourcesCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder

/** Keeps the last crash on disk so the next launch can show it instead of silently closing again. */
object CrashReport {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun record(ctx: Context, e: Throwable) {
        runCatching {
            val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
            file(ctx).writeText(
                "Haven ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL}\n\n$trace",
            )
        }
    }

    fun read(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}

/**
 * Shown on the launch after a crash. Built from plain Android views so it still works when the crash is in
 * Haven's Compose UI. The QR code opens a pre-filled GitHub issue on a phone.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReport.read(this) ?: "No crash details were saved."
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(HEARTH)
            setPadding(dp(58), dp(40), dp(58), dp(32))
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        left.addView(text("Haven stopped unexpectedly", 30f, R.font.fraunces_title, LINEN))
        left.addView(
            text(
                "Scan the code with your phone to send this report (it opens a GitHub issue; tap Submit), " +
                    "or take a photo of this screen. Then choose Try again.",
                17f, R.font.atkinson_medium, STONE,
            ).apply { setPadding(0, dp(10), dp(24), dp(20)) },
        )
        // The focused button grows a little; don't clip it at the row's edge.
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val retry = button("Try again", dp) {
            CrashReport.clear(this@CrashActivity)
            startActivity(Intent(this@CrashActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        buttons.addView(retry, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(16) })
        buttons.addView(button("Close", dp) { finish() })
        left.addView(buttons)
        val trace = text(report, 12f, null, DUST).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(20), dp(24), 0)
        }
        left.addView(ScrollView(this).apply { addView(trace); isFocusable = true }, LinearLayout.LayoutParams(-1, -1))
        root.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        val qr = qrBitmap(issueUrl(report), 520)
        if (qr != null) {
            root.addView(
                ImageView(this).apply {
                    setImageBitmap(qr)
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(8).toFloat()
                    }
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                },
                LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_VERTICAL },
            )
        }
        setContentView(root)
        retry.requestFocus()
    }

    private fun font(id: Int?): Typeface? = id?.let { runCatching { ResourcesCompat.getFont(this, it) }.getOrNull() }

    private fun text(s: String, sp: Float, fontId: Int?, color: Int) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        font(fontId)?.let { typeface = it }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** A Haven pill button: raised at rest, linen with dark text and a small lift when focused. */
    private fun button(label: String, dp: (Int) -> Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 18f
        font(R.font.atkinson_bold)?.let { typeface = it }
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        setPadding(dp(28), dp(12), dp(28), dp(12))
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(HEARTH, LINEN)))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pill(LINEN, dp(24)))
            addState(intArrayOf(), pill(RAISED, dp(24)))
        }
        setOnFocusChangeListener { v, focused -> v.animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f).setDuration(160).start() }
        setOnClickListener { onClick() }
    }

    private fun pill(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun issueUrl(report: String): String {
        val firstLine = report.lineSequence().drop(2).firstOrNull()?.take(80) ?: "Crash"
        val body = "```\n" + report.take(1400) + "\n```"
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=${enc("Crash: $firstLine")}&body=${enc(body)}"
    }

    private fun qrBitmap(content: String, size: Int): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) Color.BLACK else Color.WHITE }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    companion object {
        // Haven's colours (DESIGN.md); this screen can't use the Compose theme.
        private val HEARTH = Color.rgb(0x14, 0x12, 0x0F)
        private val RAISED = Color.rgb(0x35, 0x30, 0x2A)
        private val LINEN = Color.rgb(0xF2, 0xED, 0xE4)
        private val STONE = Color.rgb(0xB9, 0xB0, 0xA3)
        private val DUST = Color.rgb(0x8F, 0x87, 0x7B)

        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
