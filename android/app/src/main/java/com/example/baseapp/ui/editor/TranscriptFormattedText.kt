package com.example.baseapp.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.baseapp.data.SpeakerProfile
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.ui.formatTimestamp

@Composable
fun TranscriptFormattedText(
    segments: List<TranscriptSegment>,
    speakers: List<SpeakerProfile>,
    modifier: Modifier = Modifier,
) {
    val speakersById = speakers.associateBy { it.id }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        segments.forEach { segment ->
            val speaker = speakersById[segment.speaker]
            val speakerName = speaker?.name ?: segment.speaker
            val speakerColor = speaker?.let {
                Color(it.color)
            } ?: MaterialTheme.colorScheme.primary

            val formattedText = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = speakerColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append("[$speakerName]")
                }

                append(" (${formatTimestamp(segment.startMs)} - ")
                append("${formatTimestamp(segment.endMs)}): ")
                append(segment.text)
            }

            Text(
                text = formattedText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}