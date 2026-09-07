package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.util.DateUtils

/** 채팅 메시지 버블. 사용자/AI 메시지를 좌우 정렬하고 마크다운 렌더링 지원 */
@Composable
fun ChatBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            SelectionContainer {
                val renderedContent = remember(message.content) {
                    buildChatMessageAnnotatedString(message.content)
                }
                Text(
                    text = renderedContent,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }

        Text(
            text = DateUtils.formatTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

private fun buildChatMessageAnnotatedString(content: String) = buildAnnotatedString {
    var cursor = 0
    while (cursor < content.length) {
        val start = content.indexOf("**", startIndex = cursor)
        if (start < 0) {
            append(content.substring(cursor))
            break
        }

        val end = content.indexOf("**", startIndex = start + 2)
        if (end < 0) {
            append(content.substring(cursor))
            break
        }

        append(content.substring(cursor, start))
        val boldText = content.substring(start + 2, end)
        if (boldText.isEmpty()) {
            append("****")
        } else {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(boldText)
            pop()
        }
        cursor = end + 2
    }
}
