package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/** Result of translating a message's text / caption (TDLib translateMessageText). */
@Immutable
data class TranslatedText(val text: String, val entities: List<Entity>)
