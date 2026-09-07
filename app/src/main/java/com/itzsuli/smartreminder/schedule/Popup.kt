package com.itzsuli.smartreminder.schedule

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.itzsuli.smartreminder.MainActivity
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The 3-second card that slides in at the top of whatever you are doing.
 * Plain Android views on purpose: an overlay window is much simpler to keep alive from a
 * BroadcastReceiver than a Compose hierarchy, and it works while other apps are in front.
 */
object Popup {

    private const val TAG = "Popup"

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context, items: List<Reminder>, durationMs: Long, onDismissed: () -> Unit) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                showNow(app, items, durationMs.coerceIn(1500L, 9000L), handler, onDismissed)
            } catch (t: Throwable) {
                Log.e(TAG, "could not show pop-up", t)
                onDismissed()
            }
        }
    }

    private fun showNow(ctx: Context, items: List<Reminder>, durationMs: Long, handler: Handler, onDismissed: () -> Unit) {
        val wm = ctx.getSystemService(WindowManager::class.java) ?: run { onDismissed(); return }
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()

        val first = items.first()
        val routine = items.size == 1 && first.kind == ReminderKind.ROUTINE
        val accent = if (first.kind == ReminderKind.DEADLINE) 0xFF8B7CFF.toInt() else 0xFF34D399.toInt()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xF41B1930.toInt())
                setStroke(dp(1), 0x22FFFFFF)
            }
            elevation = dp(10).toFloat()
            clipToOutline = true
            isClickable = true
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emoji = TextView(ctx).apply {
            text = if (items.size == 1) first.emoji else "🔔"
            textSize = 26f
        }
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(ctx).apply {
            text = if (items.size == 1) first.title else "${items.size} reminders"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val details = TextView(ctx).apply {
            text = if (items.size == 1) first.details.ifBlank { first.rawInput }
            else items.joinToString("\n") { "${it.emoji} ${it.title}" }
            setTextColor(0xD0FFFFFF.toInt())
            textSize = 13f
            maxLines = if (items.size == 1) 2 else 4
            ellipsize = TextUtils.TruncateAt.END
        }
        column.addView(title)
        if (details.text.isNotBlank()) column.addView(details)

        row.addView(emoji, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 1000
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        }

        var dismissed = false
        fun dismiss() {
            if (dismissed) return
            dismissed = true
            card.animate().translationY(-dp(30).toFloat()).alpha(0f).setDuration(180)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    runCatching { wm.removeViewImmediate(card) }
                    onDismissed()
                }.start()
        }

        if (items.size == 1) {
            val button = TextView(ctx).apply {
                text = if (routine) "Done today" else "Done"
                setTextColor(0xFF15122A.toInt())
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(accent) }
                setOnClickListener {
                    val repo = SmartReminderApp.get(ctx).repository
                    if (routine) repo.setDoneForToday(first.id, true) else repo.setDone(first.id, true)
                    Notifier.cancel(ctx, first.id)
                    dismiss()
                }
            }
            row.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        }

        card.addView(row)
        card.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(12) })
        card.setOnClickListener {
            dismiss()
            runCatching {
                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        val screenWidth = ctx.resources.displayMetrics.widthPixels
        val statusBar = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { ctx.resources.getDimensionPixelSize(it) } ?: dp(24)
        val params = WindowManager.LayoutParams(
            min(screenWidth - dp(24), dp(480)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBar + dp(8)
        }

        card.translationY = -dp(40).toFloat()
        card.alpha = 0f
        wm.addView(card, params)
        card.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        ObjectAnimator.ofInt(progress, "progress", 1000, 0).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            start()
        }
        handler.postDelayed({ dismiss() }, durationMs + 260)
    }
}
