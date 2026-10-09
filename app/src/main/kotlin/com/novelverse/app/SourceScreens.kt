package com.novelverse.app

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
import com.novelverse.core.domain.*
import com.novelverse.core.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val template = """{
  "schemaVersion": 1,
  "id": "my-source",
  "name": "My source",
  "baseUrl": "https://fiction.example",
  "searchPath": "/search",
  "queryParameter": "q",
  "searchItem": "article.book",
  "searchTitle": "h2",
  "searchLink": "a",
  "novelTitle": "h1",
  "novelAuthor": ".author",
  "catalogItem": ".chapters li",
  "catalogLink": "a",
  "chapterContent": ".chapter-body",
  "catalogNext": "",
  "chapterNext": ""
}"""

@Composable
fun SourceScreen(vm: ReadingViewModel, novels: List<Novel>, onNovel: (String) -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var editor by rememberSaveable { mutableStateOf(false) }
    var definition by rememberSaveable { mutableStateOf(template) }
    var linkHit by remember { mutableStateOf<SearchHit?>(null) }
    if (editor) { SourceEditor(definition,{ definition=it },vm) { editor=false }; return }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Read from your sources",style=MaterialTheme.typography.headlineMedium)
            Text("Only retrieve content you are authorized to access. Site-specific extraction rules are required.")
            OutlinedButton(onClick={definition=template;editor=true}) { Text("Add or import source") }
        }
        items(sources,key={it.id}) { source ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    FilterChip(selected=selected==source.id,onClick={selected=source.id},enabled=source.enabled,label={Text(source.name)})
                    Text(source.baseUrl,style=MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick={definition=source.configuration;editor=true}) { Text("Edit / test") }; TextButton(onClick={vm.enabled(source)},enabled=!busy) { Text(if(source.enabled) "Disable" else "Enable") } }
                }
            }
        }
        item {
            if(sources.isEmpty()) Text("No sources configured. The example definition is a template, not a working website.")
            OutlinedTextField(query,{query=it},label={Text("Novel title")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Button(onClick={vm.search(selected,query)},enabled=!busy&&sources.any{it.id==selected&&it.enabled}&&query.isNotBlank()) { Text(if(busy) "Working…" else "Search selected source") }
        }
        items(hits,key={it.url}) { hit ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(hit.title,style=MaterialTheme.typography.titleMedium)
                    Text(hit.url,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={vm.add(hit,null,onNovel)},enabled=!busy) { Text("Add and load chapters") }
                    TextButton(onClick={linkHit=hit},enabled=!busy&&novels.isNotEmpty()) { Text("Link to an existing novel") }
                }
            }
        }
    }
    linkHit?.let { hit -> AlertDialog(onDismissRequest={linkHit=null},title={Text("Confirm the same novel")},text={
        LazyColumn { item { Text("Choose only if this is the same work. Similar titles alone are not proof.") }; items(novels,key={it.id}) { novel -> TextButton(onClick={vm.add(hit,novel.id,onNovel);linkHit=null}) { Text(novel.title) } } }
    },confirmButton={},dismissButton={TextButton(onClick={linkHit=null}) { Text("Cancel") }}) }
}

@Composable
private fun SourceEditor(json: String,onJson:(String)->Unit,vm:ReadingViewModel,onBack:()->Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }; var permitted by rememberSaveable { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch {
            try { val text=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                val buffer=CharArray(65_537); var count=0
                while(count<buffer.size) { val n=reader.read(buffer,count,buffer.size-count);if(n<0)break;count+=n }
                require(count<=65_536){"Definition exceeds 64 KB."};String(buffer,0,count)
            } };onJson(text) } catch(e:Exception) { fileError=e.message }
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(json)} } } catch(e:Exception){fileError=e.message} }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Source rules",style=MaterialTheme.typography.headlineMedium)
        Text("Configure selectors for this website. No scripts, credentials, custom headers or access-control bypass are supported.")
        Row { TextButton(onClick={importer.launch("application/json")}){Text("Import JSON")};TextButton(onClick={exporter.launch("source.json")}){Text("Export JSON")} }
        OutlinedTextField(json,onJson,label={Text("Version 1 CSS definition")},modifier=Modifier.fillMaxWidth(),minLines=12)
        OutlinedTextField(url,{url=it},label={Text("Permitted novel URL for testing")},modifier=Modifier.fillMaxWidth())
        Row { Checkbox(permitted,{permitted=it});Text("I am authorized to retrieve this content and have reviewed the website's terms.",Modifier.weight(1f)) }
        fileError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.testSource(json,url)},enabled=permitted&&!busy&&url.isNotBlank()){Text("Test metadata, catalog and first chapter")}
        Button(onClick={vm.saveSource(json)},enabled=permitted&&!busy){Text("Validate and save rules")}
        TextButton(onClick=onBack){Text("Back to sources")}
    }
}

@Composable
fun CatalogScreen(novelId:String,vm:ReadingViewModel,onRead:(String)->Unit) {
    val chapters by remember(novelId){vm.chapters(novelId)}.collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            Text("Chapters",style=MaterialTheme.typography.headlineMedium)
            OutlinedTextField(query,{query=it},label={Text("Find a chapter")},modifier=Modifier.fillMaxWidth())
            Row { TextButton(onClick={vm.refresh(novelId)},enabled=!busy){Text("Refresh")};TextButton(onClick={vm.download(chapters.filter{it.id in selected})},enabled=selected.isNotEmpty()){Text("Download selected (${selected.size})")} }
            if(chapters.isEmpty()) Text("No chapters linked. Add a website source in Explore, then link its search result to this novel.")
        }
        items(chapters.filter{it.title.contains(query,true)},key={it.id}) { chapter ->
            OutlinedCard(onClick={onRead(chapter.id)},modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Checkbox(chapter.id in selected,{checked->selected=if(checked)selected+chapter.id else selected-chapter.id})
                    Column(Modifier.weight(1f)) { Text(chapter.title);if(chapter.downloaded)Text("Available offline",style=MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(vm:ReadingViewModel,onRead:(String)->Unit) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    var removal by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Offline chapters",style=MaterialTheme.typography.headlineMedium)
            if(downloads.isEmpty())Text("Select chapters in a novel's chapter list to download them.")
        }
        item {
            Row { TextButton(onClick=vm::pauseDownloads){Text("Pause")};TextButton(onClick=vm::resumeDownloads){Text("Resume")};TextButton(onClick=vm::retryDownloads){Text("Retry failed")} }
            TextButton(onClick=vm::cancelDownloads){Text("Cancel pending")}
        }
        items(transfers,key={"task-${it.chapterId}"}){task->Text("${task.title}: ${task.status.lowercase()}${task.error?.let { " · $it" }.orEmpty()}",style=MaterialTheme.typography.bodySmall)}
        items(downloads,key={it.versionId}) { download ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(download.title);Text("${download.source} · ${download.characters} characters",style=MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick={onRead(download.novelId)}){Text("Open novel")};TextButton(onClick={removal=download.versionId}){Text("Remove offline copy")} }
            } }
        }
        item { TextButton(onClick=vm::clearCache){Text("Clear temporary cache")} }
    }
    removal?.let{id->AlertDialog(onDismissRequest={removal=null},title={Text("Remove offline availability?")},text={Text("The chapter may remain temporarily cached. Bookmarked versions and reading progress are retained.")},confirmButton={TextButton(onClick={vm.deleteDownload(id);removal=null}){Text("Remove")}},dismissButton={TextButton(onClick={removal=null}){Text("Cancel")}})}
}
