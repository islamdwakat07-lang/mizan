package com.islam.mizan

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * يشتغل كل ساعة تقريباً حتى لو التطبيق مسكّر.
 * ينبّه فقط لما يلزم، وكل نوع تنبيه مرة واحدة باليوم.
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        val prefs = c.getSharedPreferences("mizan", Context.MODE_PRIVATE)
        val s = JSONObject(prefs.getString("summary", null) ?: return Result.success())

        val now = LocalTime.now()
        val h = now.hour + now.minute / 60.0
        if (h < 8.5 || h > 22.5) return Result.success() // لا إزعاج بالليل

        val today = LocalDate.now()
        val todayStr = today.toString()
        val same = s.optString("date") == todayStr

        var steps = if (same) s.optLong("steps") else 0L
        try {
            if (Health.status(c) == "ok" && Health.granted(c)) steps = maxOf(steps, Health.todaySteps(c))
        } catch (_: Exception) { }

        val stepGoal = s.optLong("stepGoal", 8000)
        val water = if (same) s.optInt("water") else 0
        val waterGoal = s.optInt("waterGoal", 12)
        val eaten = if (same) s.optInt("eaten") else 0
        val budget = s.optInt("budget", 1620)

        fun note(key: String, title: String, text: String) {
            val flag = "n_${key}_$todayStr"
            if (prefs.getBoolean(flag, false)) return
            if (notify(c, key.hashCode(), title, text)) prefs.edit().putBoolean(flag, true).apply()
        }

        // المشي: بالوقت اللي يناسبك، التنبيه بس لما يضيق الوقت
        val miss = stepGoal - steps
        if (miss > 0) {
            val mins = ((miss + 99) / 100).toInt()
            val hoursLeft = (23 - h).coerceAtLeast(0.0)
            if (h >= 21 || (h >= 17 && mins > hoursLeft * 60 * 0.35))
                note("steps_late", "الوقت صار ضيق على المشي", "باقي $miss خطوة (حوالي $mins دقيقة). لازم تمشي الليلة.")
            else if (h >= 19)
                note("steps", "ما خلّصت مشي اليوم", "باقي $miss خطوة، حوالي $mins دقيقة. اختار الوقت اللي يناسبك.")
        }

        // الماء
        val frac = ((h - 8) / 14).coerceIn(0.0, 1.0)
        val expW = (waterGoal * frac).toInt()
        if (water < expW - 2) {
            note(if (h < 16) "water1" else "water2", "اشرب ماء", "شربت $water كاسات والمفروض $expW لهلأ. اشرب كاستين الآن.")
        }

        // السعرات
        if (eaten > budget) note("over", "تجاوزت سعرات اليوم", "زيادة ${eaten - budget} سعرة. باقي اليوم خضار وماء فقط.")
        if (h >= 14 && same && eaten == 0) note("nolog", "ما سجلت أكلك", "إذا أكلت شي اليوم سجّله بميزان.")

        // التمارين
        val lastEx = runCatching { LocalDate.parse(s.optString("lastWorkout")) }.getOrNull()
        if (lastEx != null && h >= 17) {
            val gap = ChronoUnit.DAYS.between(lastEx, today)
            if (gap >= 3) note("ex", "صار لك $gap أيام بدون تمارين", "20 دقيقة تمارين بطن وخواصر اليوم، بالوقت اللي يناسبك.")
        }

        // الوزن الأسبوعي (الصبح)
        val lastW = runCatching { LocalDate.parse(s.optString("lastWeight")) }.getOrNull()
        if (lastW != null && h < 11) {
            val gap = ChronoUnit.DAYS.between(lastW, today)
            if (gap >= 7) note("weight", "وزن الأسبوع", "صار لك $gap يوم ما وزنت. وزّن حالك الآن قبل الفطور.")
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(c: Context, id: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("mizan", "تنبيهات ميزان", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, "mizan")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(id, n)
        return true
    }
}
