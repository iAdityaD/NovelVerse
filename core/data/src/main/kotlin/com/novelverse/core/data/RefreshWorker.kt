package com.novelverse.core.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val dependencies=EntryPointAccessors.fromApplication(applicationContext,WorkerDependencies::class.java)
        val dao=dependencies.database().refresh()
        for(target in dao.due(System.currentTimeMillis())) {
            val now=System.currentTimeMillis()
            try {
                dependencies.reading().refresh(target.novelId)
                dao.finish(target.novelId,target.nextDue,now+TimeUnit.HOURS.toMillis(target.intervalHours.toLong()),now,now,null)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){dao.finish(target.novelId,target.nextDue,now+TimeUnit.HOURS.toMillis(1),now,target.lastSuccess,e.message?.take(300))}
        }
        deliver(dao)
        return Result.success()
    }
    private suspend fun deliver(dao:RefreshDao) {
        if(Build.VERSION.SDK_INT>=33&&applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled())return
        manager.createNotificationChannel(NotificationChannel("chapter-updates","New chapters",NotificationManager.IMPORTANCE_DEFAULT))
        for(event in dao.pendingNotifications()) {
            val intent=applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName) ?: continue
            intent.data=android.net.Uri.parse("novelverse://chapter/${event.chapterId}")
            intent.putExtra("novelId",event.novelId).putExtra("chapterId",event.chapterId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(applicationContext,event.chapterId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=android.app.Notification.Builder(applicationContext,"chapter-updates").setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("New chapter available").setContentText(event.title).setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
            manager.notify(event.chapterId,0,notification)
            dao.notified(event.chapterId)
        }
    }
    companion object {
        fun start(context:Context){
            val request=PeriodicWorkRequestBuilder<RefreshWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("catalog-refresh",ExistingPeriodicWorkPolicy.UPDATE,request)
        }
        fun stop(context:Context){WorkManager.getInstance(context).cancelUniqueWork("catalog-refresh")}
    }
}
