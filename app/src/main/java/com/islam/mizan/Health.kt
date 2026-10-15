package com.islam.mizan

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** قراءة الخطوات من Health Connect (نفس اللي بيسجلها Google Fit و Samsung Health) */
object Health {
    val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    val REQUEST = setOf(STEPS, BACKGROUND)

    fun status(ctx: Context): String = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> "ok"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"
        else -> "unavailable"
    }

    suspend fun granted(ctx: Context): Boolean =
        HealthConnectClient.getOrCreate(ctx).permissionController
            .getGrantedPermissions().contains(STEPS)

    suspend fun todaySteps(ctx: Context): Long {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val result = HealthConnectClient.getOrCreate(ctx).aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
            )
        )
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }
}
