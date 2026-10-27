package app.haven.launcher.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.haven.launcher.BuildConfig
import app.haven.launcher.MainActivity
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
            setBackgroundColor(Color.rgb(13, 16, 20))
            setPadding(dp(48), dp(32), dp(48), dp(32))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text("Haven stopped unexpectedly", 26f, bold = true))
        left.addView(
            text(
                "Scan the code with your phone to send this report (it opens a GitHub issue; tap Submit), " +
                    "or take a photo of this screen. Then choose Try again.",
                15f, color = Color.rgb(163, 173, 186),
            ).apply { setPadding(0, dp(8), 0, dp(12)) },
        )
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val retry = Button(this).apply {
            text = "Try again"
            setOnClickListener {
                CrashReport.clear(this@CrashActivity)
                startActivity(Intent(this@CrashActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }
        val dismiss = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        buttons.addView(retry)
        buttons.addView(dismiss)
        left.addView(buttons)
        val trace = text(report, 12f, color = Color.rgb(238, 241, 245)).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(12), dp(24), 0)
        }
        left.addView(ScrollView(this).apply { addView(trace); isFocusable = true }, LinearLayout.LayoutParams(-1, -1))
        root.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        val qr = qrBitmap(issueUrl(report), 520)
        if (qr != null) {
            root.addView(
                ImageView(this).apply {
                    setImageBitmap(qr)
                    setBackgroundColor(Color.WHITE)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                },
                LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_VERTICAL },
            )
        }
        setContentView(root)
        retry.requestFocus()
    }

    private fun text(s: String, sp: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
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
        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
