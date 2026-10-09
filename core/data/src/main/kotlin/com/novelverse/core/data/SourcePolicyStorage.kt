package com.novelverse.core.data

import androidx.room.*
@Entity(tableName="source_policies",foreignKeys=[
    ForeignKey(entity=NovelEntity::class,parentColumns=["id"],childColumns=["novelId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=NovelSourceEntity::class,parentColumns=["id"],childColumns=["novelSourceId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("novelId"),Index("novelSourceId")])
data class SourcePolicyEntity(@PrimaryKey val id:String,val novelId:String,val chapterId:String,val novelSourceId:String,val scope:String)
@Dao
interface SourcePolicyDao {
    @Upsert suspend fun save(policy:SourcePolicyEntity)
    @Query("SELECT * FROM source_policies WHERE novelId=:id") suspend fun policies(id:String):List<SourcePolicyEntity>
}
