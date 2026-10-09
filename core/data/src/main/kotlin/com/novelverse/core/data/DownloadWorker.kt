package com.novelverse.core.data

import android.content.Context
import androidx.work.*
import com.novelverse.core.domain.ReadingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@EntryPoint @InstallIn(SingletonComponent::class)
interface WorkerDependencies { fun reading():ReadingRepository; fun database():NovelDatabase }

class DownloadWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val dependencies=EntryPointAccessors.fromApplication(applicationContext,WorkerDependencies::class.java)
        val dao=dependencies.database().reading()
        for(task in dao.pendingTransfers()) {
            if(isStopped)return Result.retry()
            val running=task.copy(status="RUNNING",attempts=task.attempts+1,error=null)
            if(dao.claimTransfer(task.chapterId)==0)continue
            try {
                dependencies.reading().download(task.chapterId)
                dao.finishTransfer(task.chapterId,"COMPLETED",null)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){dao.finishTransfer(task.chapterId,if(running.attempts<3)"QUEUED" else "FAILED",e.message?.take(300))}
        }
        return if(dao.pendingTransfers().isEmpty())Result.success() else Result.retry()
    }
    companion object {
        fun enqueue(context:Context) {
            val work=OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("chapter-downloads",ExistingWorkPolicy.APPEND_OR_REPLACE,work)
        }
        fun cancel(context:Context){WorkManager.getInstance(context).cancelUniqueWork("chapter-downloads")}
    }
}
