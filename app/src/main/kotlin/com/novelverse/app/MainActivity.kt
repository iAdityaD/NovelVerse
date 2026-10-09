package com.novelverse.app

import android.os.Bundle
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val target=MutableStateFlow<Pair<String,String>?>(null)
    private fun readTarget(intent:Intent?) {
        val novel=intent?.getStringExtra("novelId");val chapter=intent?.getStringExtra("chapterId")
        if(novel!=null&&chapter!=null&&runCatching{java.util.UUID.fromString(novel);java.util.UUID.fromString(chapter)}.isSuccess)target.value=novel to chapter
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);readTarget(intent)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTarget(intent)
        setContent { val destination by target.collectAsStateWithLifecycle();NovelVerseApp(notificationTarget=destination) }
    }
}
