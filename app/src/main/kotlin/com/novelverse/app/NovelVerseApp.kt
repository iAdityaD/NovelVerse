package com.novelverse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.novelverse.core.designsystem.NovelVerseTheme
import com.novelverse.core.model.*

private enum class Destination(val title: String, val icon: ImageVector) {
    library("Library", Icons.Outlined.AutoStories), explore("Explore", Icons.Outlined.Explore),
    downloads("Downloads", Icons.Outlined.Download), settings("Settings", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelVerseApp(viewModel: LibraryViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "library"
    val current = Destination.entries.firstOrNull { it.name == route }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppEvent.Added -> {
                    nav.popBackStack()
                    nav.navigate("novel/${event.id}")
                }
                is AppEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    NovelVerseTheme(preferences.value.theme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current?.title ?: when (route) { "add" -> "Add a novel"; "archived" -> "Removed novels"; else -> "Novel details" }) },
                    navigationIcon = {
                        if (current == null) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (current == Destination.library) IconButton(onClick = { viewModel.clearFormError(); nav.navigate("add") }) {
                            Icon(Icons.Outlined.Add, "Add novel")
                        }
                    },
                )
            },
            bottomBar = {
                if (current != null) NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(selected = current == destination,
                            onClick = {
                                nav.navigate(destination.name) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }, icon = { Icon(destination.icon, null) }, label = { Text(destination.title) })
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(navController = nav, startDestination = "library", modifier = Modifier.padding(padding)) {
                composable("library") {
                    val state by viewModel.library.collectAsStateWithLifecycle()
                    val query by viewModel.query.collectAsStateWithLifecycle()
                    LibraryScreen(state, query, preferences.value.libraryLayout, viewModel::search,
                        { viewModel.clearFormError(); nav.navigate("add") }, { nav.navigate("novel/$it") },
                        viewModel::retry, viewModel::loadMore)
                }
                composable("explore") {
                    EmptyPage(Icons.Outlined.TravelExplore, "A world of stories, your sources",
                        "Website sources will be available in a later preview. You can already keep a personal library by adding novels manually.",
                        "Add a novel manually", { viewModel.clearFormError(); nav.navigate("add") })
                }
                composable("downloads") {
                    EmptyPage(Icons.Outlined.DownloadForOffline, "Your offline shelf",
                        "Chapter downloads are not available in this preview. Your library entries are saved on this device.",
                        "Open Library", { nav.navigate("library") { popUpTo("library"); launchSingleTop = true } })
                }
                composable("settings") { SettingsScreen(preferences, viewModel) { nav.navigate("archived") } }
                composable("archived") {
                    val state by viewModel.archived.collectAsStateWithLifecycle()
                    if (!state.loading && state.error == null && state.novels.isEmpty()) {
                        EmptyPage(Icons.Outlined.Inventory2, "No removed novels", "Novels removed from your library can be restored here.", "Back to Settings", { nav.popBackStack() })
                    } else if (state.error != null) {
                        EmptyPage(Icons.Outlined.ErrorOutline, "Removed novels unavailable", state.error!!, "Retry", viewModel::retry)
                    } else if (state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.novels, key = { it.id }) { novel ->
                            OutlinedCard(onClick = { nav.navigate("novel/${novel.id}") }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(novel.title, style = MaterialTheme.typography.titleMedium)
                                    Text("Open to restore", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                composable("add") { AddNovelScreen(viewModel) }
                composable("novel/{id}") { DetailScreen() }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: LibraryState, query: String, layout: LibraryLayout,
    onSearch: (String) -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit,
    onRetry: () -> Unit, onMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A little space for your next great story.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = query, onValueChange = onSearch, label = { Text("Search your library") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> EmptyPage(Icons.Outlined.ErrorOutline, "Library unavailable", state.error, "Retry", onRetry)
            state.novels.isEmpty() -> EmptyPage(Icons.Outlined.AutoStories,
                if (query.isBlank()) "Your next chapter starts here" else "No matching novels",
                if (query.isBlank()) "Add a novel to keep your reading list close. No account needed." else "Try another title or author.",
                if (query.isBlank()) "Add your first novel" else "Clear search", if (query.isBlank()) onAdd else ({ onSearch("") }))
            layout == LibraryLayout.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(state.novels, key = { it.id }) { NovelTile(it, onOpen) }
                if (state.novels.size % 60 == 0 && state.novels.size < 1000) item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.novels, key = { it.id }) { novel ->
                    OutlinedCard(onClick = { onOpen(novel.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(novel.title, style = MaterialTheme.typography.titleMedium)
                                Text(novel.author.ifBlank { "Author not set" }, style = MaterialTheme.typography.bodySmall)
                                Text(novel.readingStatus.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (state.novels.size % 60 == 0 && state.novels.size < 1000) item { TextButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
            }
        }
    }
}

@Composable
private fun NovelTile(novel: Novel, onOpen: (String) -> Unit) {
    Card(onClick = { onOpen(novel.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(novel.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(novel.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(novel.author.ifBlank { "Author not set" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(novel.readingStatus.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyPage(icon: ImageVector, title: String, description: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun AddNovelScreen(viewModel: LibraryViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.formError.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make room for a new story", style = MaterialTheme.typography.headlineMedium)
        Text("Add its details now. Website linking and reading will follow in a later preview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth().testTag("novel_title"), singleLine = true, enabled = !saving)
        OutlinedTextField(author, { author = it }, label = { Text("Author (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving)
        OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !saving)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { viewModel.add(title, author, description) }, enabled = !saving && title.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("save_novel")) {
            Text(if (saving) "Saving…" else "Add to Library")
        }
    }
}

@Composable
private fun DetailScreen(viewModel: DetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val writeError by viewModel.writeError.collectAsStateWithLifecycle()
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val novel = state.novel
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyPage(Icons.Outlined.ErrorOutline, "Novel unavailable", state.error!!, "Retry", viewModel::retry)
        novel == null -> EmptyPage(Icons.Outlined.SearchOff, "Novel not found", "This entry is not available on this device.", "Retry", viewModel::retry)
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(novel.title, style = MaterialTheme.typography.headlineLarge)
            Text(novel.author.ifBlank { "Author not set" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (novel.description.isNotBlank()) Text(novel.description)
            HorizontalDivider()
            Text("Reading status", style = MaterialTheme.typography.titleLarge)
            ReadingStatus.entries.forEach { status ->
                FilterChip(selected = novel.readingStatus == status, onClick = { viewModel.status(status) }, label = { Text(status.label) })
            }
            Text("No chapter source linked", style = MaterialTheme.typography.titleMedium)
            Text("This is a saved library entry. Live reading and source linking are not yet available in this preview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            writeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (novel.inLibrary) OutlinedButton(onClick = { confirmRemoval = true }) { Text("Remove from Library") }
            else {
                Text("Removed from Library. This entry is retained on your device.")
                Button(onClick = { viewModel.inLibrary(true) }) { Text("Restore to Library") }
            }
        }
    }
    if (confirmRemoval) AlertDialog(
        onDismissRequest = { confirmRemoval = false }, title = { Text("Remove from Library?") },
        text = { Text("Only the library listing is removed. The novel record and any associated data are retained. You can restore it from this screen.") },
        confirmButton = { TextButton(onClick = { viewModel.inLibrary(false); confirmRemoval = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsScreen(state: PreferenceState, viewModel: LibraryViewModel, onArchived: () -> Unit) {
    if (state.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    if (state.error != null) { EmptyPage(Icons.Outlined.ErrorOutline, "Preferences unavailable", state.error, "Retry", viewModel::retry); return }
    val preferences = state.value
    var fontSize by remember(preferences.fontSizeSp) { mutableFloatStateOf(preferences.fontSizeSp.toFloat()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Make it feel like yours", style = MaterialTheme.typography.headlineMedium)
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        AppTheme.entries.forEach { theme -> FilterChip(selected = preferences.theme == theme, onClick = { viewModel.theme(theme) }, label = { Text(theme.label) }) }
        HorizontalDivider()
        Text("Library layout", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryLayout.entries.forEach { layout -> FilterChip(selected = preferences.libraryLayout == layout, onClick = { viewModel.layout(layout) }, label = { Text(layout.label) }) }
        }
        HorizontalDivider()
        Text("Reader defaults", style = MaterialTheme.typography.titleLarge)
        Text("Saved for the upcoming reader. This sample previews your text size.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReaderMode.entries.forEach { mode -> FilterChip(selected = preferences.readerMode == mode, onClick = { viewModel.mode(mode) }, label = { Text(mode.label) }) }
        Text("Text size: ${fontSize.toInt()} sp")
        Slider(value = fontSize, onValueChange = { fontSize = it }, onValueChangeFinished = { viewModel.font(fontSize.toInt()) }, valueRange = 14f..36f, steps = 21)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
            Text("A quiet page. A new beginning. A story waiting to unfold.", fontFamily = FontFamily.Serif,
                fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp, modifier = Modifier.padding(20.dp))
        }
        HorizontalDivider()
        Text("On this device", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onArchived) { Text("Restore removed novels") }
        Text("No account, tracking, or network access in this preview. Uninstalling removes local data; backup export is not yet available.")
        Text("NovelVerse · 0.1.0 development preview", style = MaterialTheme.typography.labelMedium)
    }
}
