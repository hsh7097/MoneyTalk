package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ========== 테스트 데이터 ==========

private val userMessage = ChatMessage(
    id = 1,
    content = "이번 달 식비 얼마 썼어?",
    isUser = true,
    timestamp = System.currentTimeMillis()
)

private val aiMessage = ChatMessage(
    id = 2,
    content = "이번 달 식비 지출은 총 234,500원입니다.\n\n주요 지출처:\n1. 배달의민족 - 89,200원 (5회)\n2. 스타벅스 - 45,300원 (8회)\n3. 편의점 - 32,000원 (12회)",
    isUser = false,
    timestamp = System.currentTimeMillis()
)

private val shortUserMessage = ChatMessage(
    id = 3,
    content = "고마워",
    isUser = true,
    timestamp = System.currentTimeMillis()
)

// ========== ChatBubble Preview ==========

@Preview(showBackground = true, name = "채팅 버블 - 사용자")
@Composable
private fun ChatBubbleUserPreview() {
    MaterialTheme {
        Surface {
            ChatBubble(message = userMessage)
        }
    }
}

@Preview(showBackground = true, name = "채팅 버블 - AI 응답 (긴 텍스트)")
@Composable
private fun ChatBubbleAiPreview() {
    MaterialTheme {
        Surface {
            ChatBubble(message = aiMessage)
        }
    }
}

@Preview(showBackground = true, name = "채팅 버블 - 대화 흐름", widthDp = 360)
@Composable
private fun ChatBubbleConversationPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ChatBubble(message = userMessage)
                ChatBubble(message = aiMessage)
                ChatBubble(message = shortUserMessage)
            }
        }
    }
}

// ========== TypingIndicator Preview ==========

@Preview(showBackground = true, name = "타이핑 인디케이터")
@Composable
private fun TypingIndicatorPreview() {
    MaterialTheme {
        Surface {
            TypingIndicator()
        }
    }
}

// ========== RetryButton Preview ==========

@Preview(showBackground = true, name = "재시도 버튼")
@Composable
private fun RetryButtonPreview() {
    MaterialTheme {
        Surface {
            RetryButton(onClick = {})
        }
    }
}

// ========== ApiKeyDialog Preview ==========

@Preview(showBackground = true, name = "API 키 다이얼로그")
@Composable
private fun ApiKeyDialogPreview() {
    MaterialTheme {
        ApiKeyDialog(
            onDismiss = {},
            onConfirm = {}
        )
    }
}

// ========== GuideQuestionsOverlay Preview ==========

@Preview(showBackground = true, name = "가이드 질문 - API 키 있음", widthDp = 360, heightDp = 640)
@Composable
private fun GuideQuestionsOverlayWithKeyPreview() {
    MaterialTheme {
        GuideQuestionsOverlay(
            questions = guideQuestions,
            hasApiKey = true,
            onQuestionClick = {}
        )
    }
}

@Preview(showBackground = true, name = "가이드 질문 - API 키 없음", widthDp = 360, heightDp = 640)
@Composable
private fun GuideQuestionsOverlayNoKeyPreview() {
    MaterialTheme {
        GuideQuestionsOverlay(
            questions = guideQuestions,
            hasApiKey = false,
            onQuestionClick = {}
        )
    }
}
