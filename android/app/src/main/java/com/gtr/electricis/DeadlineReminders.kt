package com.gtr.electricis

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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

fun deadlineAt(day:String,hour:String)="${day}T${hour}:00-05:00"
fun minutesLeft(iso:String,now:java.time.Instant=java.time.Instant.now()):Long=java.time.Duration.between(now,java.time.OffsetDateTime.parse(iso).toInstant()).seconds.let { if(it>0) (it+59)/60 else Math.floorDiv(it,60) }
fun reminderState(item:ReminderItem,now:java.time.Instant=java.time.Instant.now()):String? {
    val iso=item.deadline_at ?: deadlineAt(item.due_date,"23:59")
    val minutes=runCatching { minutesLeft(iso,now) }.getOrNull() ?: return null
    return when { minutes<=0 -> "retrasada"; minutes<=180 -> "por_vencer"; else -> null }
}

private const val CHANNEL = "electricis_deadlines"
private const val WORK = "electricis_deadline_check"
private const val TAG = "deadline"

fun limaToday():LocalDate=LocalDate.now(ZoneId.of("America/Lima"))

fun deadlineState(dueDate:String,today:LocalDate):String? {
    val due=runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return null
    return when {
        due<today -> "retrasada"
        due<=today.plusDays(1) -> "por_vencer"
        else -> null
    }
}

object DeadlineReminders {
    private val lock=Any()
    private val gson=Gson()
    private fun prefs(context:Context)=context.getSharedPreferences(WORK,Context.MODE_PRIVATE)
    private fun items(context:Context):List<ReminderItem> = runCatching {
        gson.fromJson(prefs(context).getString("items","[]"),Array<ReminderItem>::class.java).toList()
    }.getOrDefault(emptyList())

    fun createChannel(context:Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Vencimiento de actividades",NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="Actividades por vencer y retrasadas"
            }
        )
    }

    fun isEnabled(context:Context):Boolean {
        createChannel(context)
        val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            channel?.importance!=NotificationManager.IMPORTANCE_NONE &&
            (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    }

    fun update(context:Context,account:String,latest:List<ReminderItem>)=synchronized(lock) {
        val p=prefs(context)
        val manager=NotificationManagerCompat.from(context)
        val sameAccount=p.getString("account",null)==account
        val dates=latest.associate { it.id to (it.deadline_at ?: it.due_date) }
        items(context).filter { !sameAccount || dates[it.id]!=(it.deadline_at ?: it.due_date) }.forEach { manager.cancel(TAG,it.id) }
        val editor=p.edit().putString("account",account).putString("items",gson.toJson(latest))
        if(!sameAccount) editor.remove("posted")
        editor.apply()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<DeadlineWorker>(15,TimeUnit.MINUTES).build()
        )
        check(context)
    }

    fun selectAccount(context:Context,account:String)=synchronized(lock) {
        val previous=prefs(context).getString("account",null)
        if(previous!=null && previous!=account) clear(context)
    }

    fun clear(context:Context)=synchronized(lock) {
        val manager=NotificationManagerCompat.from(context)
        items(context).forEach { manager.cancel(TAG,it.id) }
        prefs(context).edit().clear().apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        Unit
    }

    @SuppressLint("MissingPermission")
    fun check(context:Context)=synchronized(lock) {
        val p=prefs(context)
        if(p.getString("account",null)==null || !isEnabled(context)) return@synchronized
        val manager=NotificationManagerCompat.from(context)
        val previous=p.getStringSet("posted",emptySet()).orEmpty().toSet()
        val posted=mutableSetOf<String>()
        val today=limaToday()
        val tap=PendingIntent.getActivity(context,0,
            Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for(item in items(context)) {
            val state=reminderState(item)
            if(state==null) {
                manager.cancel(TAG,item.id)
                continue
            }
            val key="${item.id}:${item.deadline_at ?: item.due_date}:$state"
            if(key !in previous) {
                val noun=if(item.kind=="project") "Proyecto" else "Actividad"
                val title=if(state=="retrasada") "$noun fuera de plazo" else "$noun por finalizar"
                val left=minutesLeft(item.deadline_at ?: deadlineAt(item.due_date,"23:59"))
                val description="${item.title} · ${if(left>0) "Faltan $left minutos" else "Plazo vencido"}"
                val notice=NotificationCompat.Builder(context,CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title).setContentText(description)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(description))
                    .setContentIntent(tap).setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                try { manager.notify(TAG,item.id,notice) }
                catch (_:SecurityException) { return@synchronized }
            }
            posted.add(key)
        }
        p.edit().putStringSet("posted",posted).apply()
    }
}

class DeadlineWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        DeadlineReminders.check(applicationContext)
        return Result.success()
    }
}
