package com.novelverse.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Metadata only. No chapter bodies, transient cache, or credentials are exported. */
class MetadataBackup(private val database:NovelDatabase) {
    private val tables=listOf("novels","sources","novel_sources","chapters","source_chapters","chapter_mappings","content_versions","reading_progress","bookmarks","refresh_targets","release_events","source_policies")
    suspend fun export():String=withContext(Dispatchers.IO){database.withTransaction{
        val result=JSONObject().put("schemaVersion",1).put("containsWebsiteContent",false)
        val db=database.openHelper.readableDatabase
        for(table in tables){
            val rows=JSONArray()
            db.query("SELECT * FROM $table").use{cursor->
                while(cursor.moveToNext()){
                    val row=JSONObject()
                    cursor.columnNames.forEachIndexed{i,name->
                        val value:Any=when(cursor.getType(i)){android.database.Cursor.FIELD_TYPE_NULL->JSONObject.NULL;android.database.Cursor.FIELD_TYPE_INTEGER->cursor.getLong(i);android.database.Cursor.FIELD_TYPE_FLOAT->cursor.getDouble(i);else->cursor.getString(i)}
                        row.put(name,if(table=="content_versions"&&name=="offline")0 else value)
                    }
                    rows.put(row)
                }
            }
            result.put(table,rows)
        }
        result.toString().also{require(it.toByteArray().size<=10_000_000){"Metadata backup exceeds the 10 MB supported limit."}}
    }}
    suspend fun restore(json:String)=withContext(Dispatchers.IO){
        require(json.toByteArray().size<=10_000_000){"Backup exceeds 10 MB."}
        val root=JSONObject(json)
        require(root.getInt("schemaVersion")==1&&!root.optBoolean("containsWebsiteContent",true)){"Unsupported backup format."}
        database.withTransaction {
            val db=database.openHelper.writableDatabase
            var count=0
            for(table in tables){
                val allowed=mutableListOf<String>()
                db.query("PRAGMA table_info($table)").use{c->while(c.moveToNext())allowed+=c.getString(c.getColumnIndexOrThrow("name"))}
                val rows=root.getJSONArray(table)
                count+=rows.length();require(count<=100_000){"Too many backup records."}
                for(i in 0 until rows.length()){
                    val row=rows.getJSONObject(i)
                    require(row.keys().asSequence().toSet()==allowed.toSet()){ "Unexpected fields in $table." }
                    if(table=="sources") CssSource.parse(row.getString("configuration"))
                    if(table=="novel_sources") {
                        val source=row.getString("sourceId");val provider=row.getString("providerId")
                        db.query("SELECT novelId FROM novel_sources WHERE sourceId=? AND providerId=?",arrayOf(source,provider)).use{c->
                            require(!c.moveToFirst()||c.getString(0)==row.getString("novelId")){"A backup source is linked to a different local novel. Restore cancelled; reconcile the entries first."}
                        }
                    }
                    val values=allowed.map{name->if(table=="content_versions"&&name=="offline")0 else row.get(name).takeUnless{it===JSONObject.NULL}}.toTypedArray()
                    db.execSQL("INSERT OR IGNORE INTO $table (${allowed.joinToString(",")}) VALUES (${allowed.joinToString(","){"?"}})",values)
                }
            }
            val invalidRelations = listOf(
                "SELECT 1 FROM content_versions v JOIN chapters c ON c.id=v.chapterId JOIN source_chapters s ON s.id=v.sourceChapterId WHERE c.novelId!=s.novelId LIMIT 1",
                "SELECT 1 FROM bookmarks b JOIN content_versions v ON v.id=b.versionId WHERE b.chapterId!=v.chapterId OR b.paragraph<0 LIMIT 1",
                "SELECT 1 FROM source_policies p JOIN novel_sources s ON s.id=p.novelSourceId LEFT JOIN chapters c ON c.id=p.chapterId WHERE p.novelId!=s.novelId OR c.id IS NULL OR c.novelId!=p.novelId OR p.scope NOT IN ('CHAPTER','FROM','PRIMARY') LIMIT 1",
                "SELECT 1 FROM release_events r JOIN chapters c ON c.id=r.chapterId WHERE r.novelId!=c.novelId LIMIT 1",
                "SELECT 1 FROM refresh_targets WHERE intervalHours<1 OR intervalHours>168 LIMIT 1",
                "SELECT 1 FROM reading_progress WHERE characterOffset<0 OR relativeProgress<0 OR relativeProgress>1 LIMIT 1",
            )
            invalidRelations.forEach { sql -> db.query(sql).use { require(!it.moveToFirst()) { "Backup contains inconsistent reading metadata." } } }
            db.query("PRAGMA foreign_key_check").use{require(!it.moveToFirst()){ "Backup contains broken relationships." }}
        }
    }
}
