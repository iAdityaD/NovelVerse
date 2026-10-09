package com.novelverse.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="refresh_targets",foreignKeys=[ForeignKey(entity=NovelEntity::class,parentColumns=["id"],childColumns=["novelId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("nextDue")])
data class RefreshTargetEntity(@PrimaryKey val novelId:String,val intervalHours:Int,val nextDue:Long,val lastAttempt:Long,val lastSuccess:Long,val error:String?)
@Entity(tableName="release_events",foreignKeys=[ForeignKey(entity=ChapterEntity::class,parentColumns=["id"],childColumns=["chapterId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("novelId")])
data class ReleaseEventEntity(@PrimaryKey val chapterId:String,val novelId:String,val title:String,val discoveredAt:Long,val notified:Boolean)
@Dao
interface RefreshDao {
    @Query("SELECT * FROM novels WHERE inLibrary=1 AND readingStatus != 'COMPLETED' LIMIT 10000") suspend fun eligibleNovels():List<NovelEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun schedule(target:RefreshTargetEntity)
    @Query("UPDATE refresh_targets SET nextDue=:nextDue,lastAttempt=:attempt,lastSuccess=:success,error=:error WHERE novelId=:id AND nextDue=:expectedDue")
    suspend fun finish(id:String,expectedDue:Long,nextDue:Long,attempt:Long,success:Long,error:String?):Int
    @Query("DELETE FROM refresh_targets") suspend fun stopAll()
    @Query("DELETE FROM refresh_targets WHERE novelId=:id") suspend fun stop(id:String)
    @Query("SELECT t.* FROM refresh_targets t JOIN novels n ON n.id=t.novelId WHERE t.nextDue<=:now AND n.inLibrary=1 AND n.readingStatus != 'COMPLETED' ORDER BY nextDue LIMIT 5") suspend fun due(now:Long):List<RefreshTargetEntity>
    @Query("SELECT * FROM refresh_targets ORDER BY nextDue") fun schedules():Flow<List<RefreshTargetEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun release(event:ReleaseEventEntity)
    @Query("SELECT * FROM release_events ORDER BY discoveredAt DESC LIMIT 500") fun releases():Flow<List<ReleaseEventEntity>>
    @Query("SELECT * FROM release_events WHERE notified=0 LIMIT 100") suspend fun pendingNotifications():List<ReleaseEventEntity>
    @Query("UPDATE release_events SET notified=1 WHERE chapterId=:id") suspend fun notified(id:String)
}
