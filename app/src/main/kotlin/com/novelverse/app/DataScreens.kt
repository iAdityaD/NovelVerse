package com.novelverse.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun UpdatesScreen(vm:ReadingViewModel,onRead:(String,String)->Unit) {
    val schedules by vm.schedules.collectAsStateWithLifecycle();val releases by vm.releases.collectAsStateWithLifecycle()
    var hours by rememberSaveable { mutableStateOf("6") }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    fun time(value:Long)=if(value==0L)"Never" else DateFormat.getDateTimeInstance().format(Date(value))
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Updates",style=MaterialTheme.typography.headlineMedium)
            Text("Checks are spread over the interval and run when Android permits. Completed novels are paused. This schedules the novels currently linked to sources.")
            OutlinedTextField(hours,{hours=it},label={Text("Interval in hours (1–168)")},singleLine=true)
            Button(onClick={hours.toIntOrNull()?.let{vm.schedule(it)}},enabled=hours.toIntOrNull()?.let{it in 1..168}==true){Text("Schedule linked novels")}
            TextButton(onClick={vm.schedule(0)}){Text("Manual refresh only")}
            if(Build.VERSION.SDK_INT>=33)TextButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow release notifications")}
        }
        items(schedules,key={it.novelId}){schedule->
            OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                Text("Every ${schedule.intervalHours} hours")
                Text("Last attempt: ${time(schedule.lastAttempt)}\nLast success: ${time(schedule.lastSuccess)}\nNext eligible: ${time(schedule.nextDue)}",style=MaterialTheme.typography.bodySmall)
                schedule.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                Row{TextButton(onClick={vm.refresh(schedule.novelId)}){Text("Refresh now")};TextButton(onClick={vm.schedule(0,schedule.novelId)}){Text("Pause this novel")}}
            }}
        }
        item{Text("Release activity",style=MaterialTheme.typography.titleLarge);if(releases.isEmpty())Text("No confirmed new chapters yet.")}
        items(releases,key={it.chapterId}){release->TextButton(onClick={onRead(release.novelId,release.chapterId)}){Text("${release.title} · ${time(release.discoveredAt)}")}}
    }
}

@Composable
fun BackupScreen(vm:ReadingViewModel) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var status by rememberSaveable { mutableStateOf("") };var busy by remember{mutableStateOf(false)}
    var restore by remember{mutableStateOf<String?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{
        busy=true
        try{val json=vm.exportMetadata();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(json)}};status="Metadata backup saved."}
        catch(e:Exception){status="Backup failed: ${e.message}"}finally{busy=false}
    }}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{
        busy=true
        try{restore=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.bufferedReader().use{reader->
            val output=StringBuilder();val buffer=CharArray(8192)
            while(true){val n=reader.read(buffer);if(n<0)break;output.append(buffer,0,n);require(output.length<=10_000_000){"Backup exceeds 10 MB."}}
            output.toString()
        }}}catch(e:Exception){status="Could not read backup: ${e.message}"}finally{busy=false}
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Your library, kept safe",style=MaterialTheme.typography.headlineMedium)
        Text("Export library metadata, source definitions, chapter mappings, reading positions, bookmarks and schedules. Chapter text and offline files are excluded. Existing local records are retained when restoring matching IDs.")
        Text("This export is not encrypted. Keep it in a private location; notes and source URLs are included.")
        Button(onClick={export.launch("NovelVerse-backup.json")},enabled=!busy){Text("Export metadata backup")}
        OutlinedButton(onClick={importer.launch("application/json")},enabled=!busy){Text("Restore metadata")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status)
    }
    restore?.let{json->AlertDialog(onDismissRequest={restore=null},title={Text("Restore this backup?")},text={Text("Validated metadata will be added transactionally. Existing matching records are retained. Offline chapter bodies are not included, so they must be downloaded again. Conflicting source-to-novel links cancel the restore.")},confirmButton={TextButton(onClick={restore=null;scope.launch{busy=true;try{vm.restoreMetadata(json);status="Metadata restored. Existing local records retained."}catch(e:Exception){status="Restore failed: ${e.message}"}finally{busy=false}}}){Text("Restore")}},dismissButton={TextButton(onClick={restore=null}){Text("Cancel")}})}
}
