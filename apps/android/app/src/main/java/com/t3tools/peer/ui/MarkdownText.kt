package com.t3tools.peer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the markdown the agents write: bold, italic, inline code, headings
 * and bullets. Everything else stays as it is.
 */
@Composable
fun MarkdownText(
  text: String,
  color: Color,
  fontSizeSp: Float = 15f,
  lineHeightSp: Float = 22f,
  codeBackground: Color = Color.Transparent,
  modifier: Modifier = Modifier,
) {
  val fontSize = fontSizeSp.sp
  val lineHeight = lineHeightSp.sp
  val blocks = remember(text) { text.split("\n") }
  Column(modifier = modifier.fillMaxWidth()) {
    blocks.forEach { block ->
      when {
        block.isBlank() -> Text("", fontSize = fontSize / 2, color = color)

        block.startsWith("### ") ->
          Text(
            inlineMarkdown(block.removePrefix("### "), color, codeBackground),
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(top = 6.dp),
          )

        block.startsWith("## ") ->
          Text(
            inlineMarkdown(block.removePrefix("## "), color, codeBackground),
            fontSize = (fontSizeSp + 1f).sp,
            lineHeight = (lineHeightSp + 2f).sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(top = 8.dp),
          )

        block.startsWith("# ") ->
          Text(
            inlineMarkdown(block.removePrefix("# "), color, codeBackground),
            fontSize = (fontSizeSp + 3f).sp,
            lineHeight = (lineHeightSp + 4f).sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
          )

        isBullet(block) ->
          Row(modifier = Modifier.padding(start = 6.dp)) {
            Text("•", fontSize = fontSize, lineHeight = lineHeight, color = color)
            Text(
              inlineMarkdown(bulletText(block), color, codeBackground),
              fontSize = fontSize,
              lineHeight = lineHeight,
              color = color,
              modifier = Modifier.padding(start = 8.dp),
            )
          }

        else ->
          Text(
            inlineMarkdown(block, color, codeBackground),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
          )
      }
    }
  }
}

private fun isBullet(block: String): Boolean =
  block.startsWith("- ") || block.startsWith("* ") || block.startsWith("• ")

private fun bulletText(block: String): String =
  when {
    block.startsWith("- ") -> block.removePrefix("- ")
    block.startsWith("* ") -> block.removePrefix("* ")
    else -> block.removePrefix("• ")
  }

private fun inlineMarkdown(text: String, color: Color, codeBackground: Color): AnnotatedString =
  buildAnnotatedString {
    var index = 0
    while (index < text.length) {
      val character = text[index]
      when {
        text.startsWith("**", index) -> {
          val end = text.indexOf("**", index + 2)
          if (end < 0) {
            append(character)
            index += 1
          } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
              append(text.substring(index + 2, end))
            }
            index = end + 2
          }
        }

        text.startsWith("__", index) -> {
          val end = text.indexOf("__", index + 2)
          if (end < 0) {
            append(character)
            index += 1
          } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
              append(text.substring(index + 2, end))
            }
            index = end + 2
          }
        }

        character == '`' -> {
          val end = text.indexOf('`', index + 1)
          if (end < 0) {
            append(character)
            index += 1
          } else {
            withStyle(
              SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBackground,
              ),
            ) {
              append(text.substring(index + 1, end))
            }
            index = end + 1
          }
        }

        character == '*' || character == '_' -> {
          val end = text.indexOf(character, index + 1)
          if (end < 0) {
            append(character)
            index += 1
          } else {
            withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
              append(text.substring(index + 1, end))
            }
            index = end + 1
          }
        }

        else -> {
          append(character)
          index += 1
        }
      }
    }
  }

@Composable
private fun <T> rememberUnused(key: Any?, calculation: () -> T): T = calculation()
