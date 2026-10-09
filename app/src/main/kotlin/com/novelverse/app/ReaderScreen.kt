package com.novelverse.app

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelverse.core.domain.*
import com.novelverse.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private data class ReaderPage(val text:String,val paragraph:Int,val paragraphOffset:Int)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(novelId:String,chapterId:String,vm:ReadingViewModel,preferences:UserPreferences,onMode:(ReaderMode)->Unit,onBack:()->Unit,onChapter:(String)->Unit) {
    val state by vm.reader.collectAsStateWithLifecycle()
    val bookmarks by remember(chapterId){vm.bookmarks(chapterId)}.collectAsStateWithLifecycle(emptyList())
    val choices by vm.choices.collectAsStateWithLifecycle()
    val chapters by remember(novelId){vm.chapters(novelId)}.collectAsStateWithLifecycle(emptyList())
    var controls by rememberSaveable { mutableStateOf(true) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<ChapterChoice?>(null) }
    var sourceScope by rememberSaveable { mutableStateOf("CHAPTER") }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(novelId,chapterId){vm.open(novelId,chapterId)}
    val version=state.version?.takeIf{state.chapterId==chapterId}
    val chapterIndex=chapters.indexOfFirst{it.id==chapterId}
    Column(Modifier.fillMaxSize()) {
        if(controls) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onBack){Text("Back")}
                TextButton(onClick={sourcesOpen=true},enabled=version!=null){Text("Source")}
                TextButton(onClick={noteOpen=true},enabled=version!=null){Text("Bookmark")}
                TextButton(onClick={controls=false}){Text("Hide")}
            }
            Text(chapters.getOrNull(chapterIndex)?.title ?: "Chapter",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(horizontal=20.dp))
            Row {
                TextButton(onClick={onMode(if(preferences.readerMode==ReaderMode.CONTINUOUS)ReaderMode.PAGINATED else ReaderMode.CONTINUOUS)}){Text(preferences.readerMode.label)}
                TextButton(onClick={vm.download(chapters.filter{it.id==chapterId})},enabled=version!=null){Text("Download")}
                TextButton(onClick={vm.open(novelId,chapterId,force=true)}){Text("Retry extraction")}
            }
            version?.let{Text("${it.sourceName} · ${if(it.offline)"Offline copy" else "Temporary cache"}",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=20.dp))}
        }
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        version?.warning?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp)) }
        if(version==null) {
            Column(Modifier.weight(1f).padding(24.dp)){Text(if(state.loading)"Loading chapter…" else "Chapter unavailable. Retry, enable a source, or open a downloaded chapter.");Button(onClick={vm.open(novelId,chapterId)}){Text("Retry")}}
        } else {
            val reveal=Modifier.weight(1f).pointerInput(version.id){detectTapGestures(onTap={controls=!controls})}
            if(preferences.readerMode==ReaderMode.CONTINUOUS) {
                val list=rememberLazyListState()
                var restored by remember(version.id){mutableStateOf(false)}
                LaunchedEffect(version.id){
                    val position=state.position
                    list.scrollToItem((position?.paragraph ?: 0).coerceIn(version.paragraphs.indices),if(position?.kind=="SCROLL")position.offset else 0)
                    restored=true
                }
                LaunchedEffect(list,version.id,restored){if(restored) snapshotFlow{list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset}.distinctUntilChanged().debounce(300).collect{(i,offset)->vm.position(i,offset)}}
                LazyColumn(state=list,modifier=reveal,contentPadding=PaddingValues(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    items(version.paragraphs.size,key={"${version.id}-$it"}){i->SelectionContainer{Text(version.paragraphs[i],fontFamily=FontFamily.Serif,fontSize=preferences.fontSizeSp.sp,lineHeight=(preferences.fontSizeSp*1.6f).sp)}}
                }
            } else PaginatedText(version,state.position,preferences.fontSizeSp,reveal){paragraph,offset->vm.position(paragraph,offset,"TEXT")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={chapters.getOrNull(chapterIndex-1)?.let{onChapter(it.id)}},enabled=chapterIndex>0){Text("Previous")}
            TextButton(onClick={controls=!controls}){Text("${((state.position?.fraction ?: 0.0)*100).toInt()}% · Controls")}
            TextButton(onClick={chapters.getOrNull(chapterIndex+1)?.let{onChapter(it.id)}},enabled=chapterIndex>=0&&chapterIndex<chapters.lastIndex){Text("Next")}
        }
    }
    if(sourcesOpen) AlertDialog(onDismissRequest={sourcesOpen=false},title={Text("Choose chapter source")},text={
        Column {
            listOf("CHAPTER" to "This chapter only","FROM" to "From here onward","PRIMARY" to "Make primary").forEach{(value,label)->FilterChip(selected=sourceScope==value,onClick={sourceScope=value},label={Text(label)})}
            OutlinedTextField(sourceQuery,{sourceQuery=it},label={Text("Find equivalent chapter")})
            LazyColumn { items(choices.filter{it.title.contains(sourceQuery,true)},key={it.sourceChapterId}){choice->
                TextButton(onClick={if(choice.confirmed){vm.open(novelId,chapterId,choice,scope=sourceScope);sourcesOpen=false}else confirm=choice}){Text("${choice.sourceName}: ${choice.title}${if(choice.confirmed)"" else " · Review required"}")}
            } }
        }
    },confirmButton={TextButton(onClick={sourcesOpen=false}){Text("Close")}})
    confirm?.let{choice->AlertDialog(onDismissRequest={confirm=null},title={Text("Confirm chapter identity")},text={Text("Is ‘${choice.title}’ from ${choice.sourceName} the same chapter as ‘${chapters.getOrNull(chapterIndex)?.title}’? A confirmed mapping will be saved. Do not match by number alone.")},confirmButton={TextButton(onClick={vm.open(novelId,chapterId,choice,scope=sourceScope);confirm=null;sourcesOpen=false}){Text("Same chapter")}},dismissButton={TextButton(onClick={confirm=null}){Text("Cancel")}})}
    if(noteOpen) AlertDialog(onDismissRequest={noteOpen=false},title={Text("Bookmark this paragraph")},text={Column{
        OutlinedTextField(note,{note=it},label={Text("Personal note (optional)")})
        LazyColumn(Modifier.heightIn(max=240.dp)){items(bookmarks,key={it.id}){saved->TextButton(onClick={vm.openBookmark(saved);noteOpen=false}){Text("Paragraph ${saved.paragraph+1}: ${saved.note.ifBlank{"Bookmark"}}")}}}
    },confirmButton={TextButton(onClick={vm.bookmark(note);note="";noteOpen=false}){Text("Save")}},dismissButton={TextButton(onClick={noteOpen=false}){Text("Cancel")}})
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun PaginatedText(version:ChapterVersion,position:ReaderPosition?,fontSize:Int,modifier:Modifier,onPosition:(Int,Int)->Unit) {
    val measurer=rememberTextMeasurer(cacheSize=2)
    val density=LocalDensity.current
    BoxWithConstraints(modifier.padding(horizontal=24.dp,vertical=16.dp)) {
        val width=with(density){maxWidth.roundToPx()}.coerceAtLeast(1)
        val height=with(density){maxHeight.roundToPx()}.coerceAtLeast(1)
        val style=TextStyle(fontFamily=FontFamily.Serif,fontSize=fontSize.sp,lineHeight=(fontSize*1.6f).sp)
        val pages by produceState<List<ReaderPage>>(emptyList(),version.id,width,height,fontSize,density.fontScale) {
            value=withContext(Dispatchers.Default) {
                val starts=mutableListOf<Int>();var length=0
                version.paragraphs.forEach{starts+=length;length+=it.length+2}
                val text=version.paragraphs.joinToString("\n\n");val result=mutableListOf<ReaderPage>();var offset=0;var paragraph=0
                while(offset<text.length) {
                    val sample=text.substring(offset,(offset+8000).coerceAtMost(text.length))
                    val layout=measurer.measure(AnnotatedString(sample),style=style,constraints=Constraints(maxWidth=width),skipCache=true)
                    var line=0
                    while(line+1<layout.lineCount&&layout.getLineBottom(line+1)<=height)line++
                    val count=layout.getLineEnd(line,visibleEnd=false).coerceAtLeast(1)
                    while(paragraph+1<starts.size&&starts[paragraph+1]<=offset)paragraph++
                    result+=ReaderPage(sample.take(count),paragraph,offset-starts[paragraph]);offset+=count
                }
                result
            }
        }
        if(pages.isEmpty()) CircularProgressIndicator() else {
            val pager=rememberPagerState(pageCount={pages.size})
            var restored by remember(version.id,width,height,fontSize){mutableStateOf(false)}
            LaunchedEffect(pages){pager.scrollToPage(pages.indexOfLast{it.paragraph<(position?.paragraph ?: 0)||(it.paragraph==(position?.paragraph ?: 0)&&it.paragraphOffset<=(if(position?.kind=="TEXT")position.offset else 0))}.coerceAtLeast(0));restored=true}
            LaunchedEffect(pager,pages,restored){if(restored)snapshotFlow{pager.currentPage}.distinctUntilChanged().debounce(300).collect{onPosition(pages[it].paragraph,pages[it].paragraphOffset)}}
            HorizontalPager(state=pager,modifier=Modifier.fillMaxSize()){index->SelectionContainer{Text(pages[index].text,style=style,modifier=Modifier.fillMaxSize())}}
        }
    }
}
