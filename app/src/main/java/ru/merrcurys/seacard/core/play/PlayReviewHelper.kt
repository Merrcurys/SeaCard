package ru.merrcurys.seacard.core.play

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play In-app Review — см. [документацию](https://developer.android.com/guide/playcore/in-app-review).
 *
 * Частоту на стороне Play ограничивает Google; здесь дополнительно не чаще чем раз в [MIN_INTERVAL_MS].
 */
object PlayReviewHelper {
    private const val TAG = "PlayReview"
    private const val PREFS_NAME = "seacard_play"
    private const val KEY_LAST_REVIEW_SUCCESS_MS = "review_last_success_request_ms"

    /** Минимальный интервал между **успешными** requestReviewFlow (как у RuStore). */
    private const val MIN_INTERVAL_MS = 14L * 24 * 60 * 60 * 1000 // 14 дней

    private fun formatElapsed(ms: Long): String {
        val minutes = (ms / 60_000).toInt().coerceAtLeast(1)
        val hours = ms / 3_600_000
        val days = ms / 86_400_000
        return when {
            days >= 1 -> "$days сут. ${(ms % 86_400_000) / 3_600_000} ч"
            hours >= 1 -> "$hours ч ${(ms % 3_600_000) / 60_000} мин"
            else -> "$minutes мин"
        }
    }

    fun tryLaunchReview(activity: ComponentActivity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(KEY_LAST_REVIEW_SUCCESS_MS, 0L)
        if (lastAttempt != 0L && now - lastAttempt < MIN_INTERVAL_MS) {
            val elapsed = now - lastAttempt
            Log.i(
                TAG,
                "Пропуск: кулдаун 14 дней (прошло ${formatElapsed(elapsed)} с последнего успешного requestReviewFlow)",
            )
            return
        }
        Log.i(TAG, "Запрос review: requestReviewFlow()")

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                prefs.edit().putLong(KEY_LAST_REVIEW_SUCCESS_MS, System.currentTimeMillis()).apply()
                Log.i(TAG, "requestReviewFlow OK, launchReviewFlow()")
                manager.launchReviewFlow(activity, reviewInfo)
                    .addOnSuccessListener {
                        Log.i(TAG, "launchReviewFlow: пользователь закрыл форму (onSuccess)")
                    }
                    .addOnFailureListener { t ->
                        Log.i(TAG, "launchReviewFlow onFailure (часто норма: лимит Play / не из Play)", t)
                    }
            }
            .addOnFailureListener { t ->
                Log.i(
                    TAG,
                    "requestReviewFlow onFailure: приложение не из Play, лимит, уже оценил и т.д.",
                    t,
                )
            }
    }
}
