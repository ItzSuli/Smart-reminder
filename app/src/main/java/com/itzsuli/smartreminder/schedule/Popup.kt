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
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.itzsuli.smartreminder.MainActivity
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.PopupPosition
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The 3-second card that appears over whatever you are doing.
 * Plain Android views on purpose: an overlay window is much simpler to keep alive from a
 * BroadcastReceiver than a Compose hierarchy, and it works while other apps are in front.
 */
object Popup {

    private const val TAG = "Popup"

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        items: List<Reminder>,
        durationMs: Long,
        position: PopupPosition = PopupPosition.UPPER_MIDDLE,
        onDismissed: () -> Unit,
    ) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                showNow(app, items, durationMs.coerceIn(1500L, 9000L), position, handler, onDismissed)
            } catch (t: Throwable) {
                Log.e(TAG, "could not show pop-up", t)
                onDismissed()
            }
        }
    }

    private fun showNow(
        ctx: Context,
        items: List<Reminder>,
        durationMs: Long,
        position: PopupPosition,
        handler: Handler,
        onDismissed: () -> Unit,
    ) {
        val wm = ctx.getSystemService(WindowManager::class.java) ?: run { onDismissed(); return }
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).roundToInt()

        val first = items.first()
        val single = items.size == 1
        val accent = when {
            !single -> 0xFF8B7CFF.toInt()
            first.kind == ReminderKind.ROUTINE -> 0xFF34D399.toInt()
            first.kind == ReminderKind.EVENT -> 0xFFFBBF24.toInt()
            else -> 0xFF8B7CFF.toInt()
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(0xF41B1930.toInt())
                setStroke(dp(1), 0x26FFFFFF)
            }
            elevation = dp(14).toFloat()
            clipToOutline = true
            isClickable = true
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emoji = TextView(ctx).apply {
            text = if (single) first.emoji else "🔔"
            textSize = 28f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0x1FFFFFFF) }
            minWidth = dp(52); minHeight = dp(52)
        }
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val kicker = TextView(ctx).apply {
            text = when {
                !single -> "${items.size} reminders"
                first.kind == ReminderKind.ROUTINE -> "Daily"
                first.kind == ReminderKind.EVENT -> "Coming up"
                else -> "Reminder"
            }.uppercase()
            setTextColor(accent)
            textSize = 10.5f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = TextView(ctx).apply {
            text = if (single) first.title else items.joinToString(" · ") { it.title }
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = if (single) 2 else 3
            ellipsize = TextUtils.TruncateAt.END
        }
        val details = TextView(ctx).apply {
            text = if (single) first.details.ifBlank { first.rawInput }
            else items.joinToString("\n") { "${it.emoji} ${it.title}" }
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 13f
            maxLines = if (single) 2 else 4
            ellipsize = TextUtils.TruncateAt.END
        }
        column.addView(kicker)
        column.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        if (single && details.text.isNotBlank()) column.addView(details, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })

        row.addView(emoji, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 1000
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(0x2EFFFFFF)
        }

        var dismissed = false
        fun dismiss() {
            if (dismissed) return
            dismissed = true
            card.animate().scaleX(0.94f).scaleY(0.94f).alpha(0f).setDuration(160)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    runCatching { wm.removeViewImmediate(card) }
                    onDismissed()
                }.start()
        }

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        if (single) {
            val button = TextView(ctx).apply {
                text = when (first.kind) {
                    ReminderKind.ROUTINE -> "Done today"
                    ReminderKind.EVENT -> "Got it"
                    ReminderKind.DEADLINE -> "Done"
                }
                setTextColor(0xFF15122A.toInt())
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(accent) }
                setOnClickListener {
                    val repo = SmartReminderApp.get(ctx).repository
                    when (first.kind) {
                        ReminderKind.ROUTINE -> repo.setDoneForToday(first.id, true)
                        ReminderKind.DEADLINE -> repo.setDone(first.id, true)
                        ReminderKind.EVENT -> {}
                    }
                    Notifier.cancel(ctx, first.id)
                    dismiss()
                }
            }
            actions.addView(button)
        }

        card.addView(row)
        if (actions.childCount > 0) {
            card.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        card.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(12) })
        card.setOnClickListener {
            dismiss()
            runCatching {
                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        val metrics = ctx.resources.displayMetrics
        val statusBar = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { ctx.resources.getDimensionPixelSize(it) } ?: dp(24)
        val params = WindowManager.LayoutParams(
            min(metrics.widthPixels - dp(28), dp(440)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            when (position) {
                PopupPosition.TOP -> { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = statusBar + dp(8) }
                PopupPosition.UPPER_MIDDLE -> { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (metrics.heightPixels * 0.26f).roundToInt() }
                PopupPosition.CENTER -> { gravity = Gravity.CENTER; y = 0 }
            }
        }

        if (position == PopupPosition.TOP) {
            card.translationY = -dp(40).toFloat()
        } else {
            card.scaleX = 0.88f
            card.scaleY = 0.88f
        }
        card.alpha = 0f
        wm.addView(card, params)
        card.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(280)
            .setInterpolator(OvershootInterpolator(1.1f)).start()
        ObjectAnimator.ofInt(progress, "progress", 1000, 0).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            start()
        }
        handler.postDelayed({ dismiss() }, durationMs + 280)
    }
}
