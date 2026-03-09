package com.sanha.moneytalk.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sanha.moneytalk.core.model.CategoryProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberCategoryEmoji(displayName: String): String {
    val context = LocalContext.current
    val provider = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CategoryProvider.Provider::class.java
        ).categoryProvider()
    }
    var emoji by remember(displayName) {
        mutableStateOf(provider.resolveEmoji(displayName))
    }

    LaunchedEffect(provider, displayName) {
        withContext(Dispatchers.IO) {
            provider.getCustomCategories()
        }
        emoji = provider.resolveEmoji(displayName)
    }

    return emoji
}
