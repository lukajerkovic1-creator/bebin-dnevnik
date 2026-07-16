@file:Suppress("MatchingDeclarationName")

package hr.bebindnevnik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class GrowthIllustrationKind {
    SCALE,
    RULER,
    HEAD_TAPE,
    BABY_GROWTH,
}

@Composable
internal fun GrowthIllustration(
    kind: GrowthIllustrationKind,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Canvas(modifier.semantics { contentDescription = description }) {
        val stroke = Stroke(size.minDimension * .045f, cap = StrokeCap.Round)
        when (kind) {
            GrowthIllustrationKind.SCALE -> {
                drawCircle(colors.primaryContainer, size.minDimension * .45f, center)
                drawRoundRect(colors.surface, Offset(size.width * .15f, size.height * .28f), Size(size.width * .7f, size.height * .56f), CornerRadius(size.width * .15f))
                drawArc(colors.primary, 200f, 140f, false, Offset(size.width * .3f, size.height * .35f), Size(size.width * .4f, size.height * .34f), style = stroke)
                drawLine(colors.primary, center, Offset(size.width * .62f, size.height * .43f), stroke.width, StrokeCap.Round)
                drawCircle(colors.primary, stroke.width * .7f, center)
            }

            GrowthIllustrationKind.RULER -> {
                drawRoundRect(colors.tertiaryContainer, Offset(size.width * .12f, size.height * .3f), Size(size.width * .76f, size.height * .4f), CornerRadius(size.width * .08f))
                repeat(8) { index ->
                    val x = size.width * (.18f + index * .09f)
                    val top = if (index % 2 == 0) size.height * .39f else size.height * .47f
                    drawLine(colors.onTertiaryContainer, Offset(x, top), Offset(x, size.height * .63f), stroke.width * .45f)
                }
                drawCircle(colors.primary, size.width * .055f, Offset(size.width * .2f, size.height * .2f))
            }

            GrowthIllustrationKind.HEAD_TAPE -> {
                drawCircle(colors.primaryContainer, size.width * .3f, Offset(size.width * .48f, size.height * .47f))
                drawArc(colors.primary, 155f, 245f, false, Offset(size.width * .14f, size.height * .2f), Size(size.width * .68f, size.height * .55f), style = stroke)
                drawRoundRect(colors.tertiaryContainer, Offset(size.width * .2f, size.height * .65f), Size(size.width * .62f, size.height * .14f), CornerRadius(size.width * .05f))
                repeat(6) { index ->
                    val x = size.width * (.28f + index * .085f)
                    drawLine(colors.onTertiaryContainer, Offset(x, size.height * .66f), Offset(x, size.height * .73f), stroke.width * .35f)
                }
            }

            GrowthIllustrationKind.BABY_GROWTH -> {
                drawCircle(colors.primaryContainer, size.width * .18f, Offset(size.width * .38f, size.height * .3f))
                drawOval(colors.tertiaryContainer, Offset(size.width * .23f, size.height * .42f), Size(size.width * .34f, size.height * .38f))
                drawLine(colors.primary, Offset(size.width * .69f, size.height * .78f), Offset(size.width * .69f, size.height * .2f), stroke.width)
                repeat(4) { index ->
                    val y = size.height * (.27f + index * .14f)
                    drawLine(colors.primary, Offset(size.width * .65f, y), Offset(size.width * .78f, y), stroke.width * .6f)
                }
                drawLine(colors.primary, Offset(size.width * .62f, size.height * .31f), Offset(size.width * .69f, size.height * .2f), stroke.width, StrokeCap.Round)
                drawLine(colors.primary, Offset(size.width * .76f, size.height * .31f), Offset(size.width * .69f, size.height * .2f), stroke.width, StrokeCap.Round)
            }
        }
    }
}
