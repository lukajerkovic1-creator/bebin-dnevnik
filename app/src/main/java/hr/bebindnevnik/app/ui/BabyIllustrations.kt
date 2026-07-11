package hr.bebindnevnik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class BabyIllustrationKind {
    BOTTLE,
    DROPS,
    EXERCISE,
    STOOL,
    TUMMY,
    JOURNAL,
}

internal enum class BabyNavKind {
    TODAY,
    CALENDAR,
    STATISTICS,
    SETTINGS,
}

private data class IllustrationColors(
    val primary: Color,
    val strong: Color,
    val skin: Color,
    val milk: Color,
    val accent: Color,
    val soft: Color,
    val line: Color,
)

@Composable
internal fun BabyIllustration(
    kind: BabyIllustrationKind,
    modifier: Modifier = Modifier,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val colors =
        IllustrationColors(
            primary = scheme.primary,
            strong = if (scheme.background.luminance() < .5f) Color(0xFFFFD0E2) else BabyPalette.FuchsiaDark,
            skin = if (scheme.background.luminance() < .5f) Color(0xFFFFCFAF) else Color(0xFFF4B58E),
            milk = if (scheme.background.luminance() < .5f) Color(0xFFFFF4D7) else Color(0xFFFFF9E8),
            accent = if (scheme.background.luminance() < .5f) Color(0xFFD8C8F2) else BabyPalette.Lavender,
            soft = scheme.primaryContainer,
            line = scheme.onSurfaceVariant,
        )
    Canvas(
        modifier.testTag("illustration-${kind.name.lowercase()}"),
    ) {
        when (kind) {
            BabyIllustrationKind.BOTTLE -> drawBottle(colors)
            BabyIllustrationKind.DROPS -> drawDrops(colors)
            BabyIllustrationKind.EXERCISE -> drawExercise(colors)
            BabyIllustrationKind.STOOL -> drawStool(colors)
            BabyIllustrationKind.TUMMY -> drawTummy(colors)
            BabyIllustrationKind.JOURNAL -> drawJournal(colors)
        }
    }
}

@Composable
internal fun BabyNavIcon(
    kind: BabyNavKind,
    selected: Boolean,
    label: String,
) {
    val color =
        if (selected) {
            androidx.compose.material3.MaterialTheme.colorScheme.primary
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        }
    Canvas(
        Modifier
            .size(28.dp)
            .semantics { contentDescription = label },
    ) {
        val stroke = Stroke(width = size.minDimension * .075f, cap = StrokeCap.Round)
        when (kind) {
            BabyNavKind.TODAY -> {
                drawRoundRect(color, Offset(size.width * .31f, size.height * .3f), Size(size.width * .38f, size.height * .52f), CornerRadius(6f), stroke)
                drawLine(color, Offset(size.width * .37f, size.height * .3f), Offset(size.width * .37f, size.height * .18f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * .63f, size.height * .3f), Offset(size.width * .63f, size.height * .18f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * .31f, size.height * .43f), Offset(size.width * .69f, size.height * .43f), stroke.width)
                drawCircle(color, size.width * .055f, Offset(size.width * .5f, size.height * .61f))
            }

            BabyNavKind.CALENDAR -> {
                drawRoundRect(color, Offset(size.width * .16f, size.height * .21f), Size(size.width * .68f, size.height * .63f), CornerRadius(7f), style = stroke)
                drawLine(color, Offset(size.width * .16f, size.height * .41f), Offset(size.width * .84f, size.height * .41f), stroke.width)
                listOf(.34f, .5f, .66f).forEach { x -> drawCircle(color, 2.2f, Offset(size.width * x, size.height * .59f)) }
            }

            BabyNavKind.STATISTICS -> {
                drawRoundRect(color, Offset(size.width * .2f, size.height * .56f), Size(size.width * .13f, size.height * .25f), CornerRadius(4f))
                drawRoundRect(color, Offset(size.width * .43f, size.height * .38f), Size(size.width * .13f, size.height * .43f), CornerRadius(4f))
                drawRoundRect(color, Offset(size.width * .66f, size.height * .2f), Size(size.width * .13f, size.height * .61f), CornerRadius(4f))
            }

            BabyNavKind.SETTINGS -> {
                drawCircle(color, size.width * .16f, center, style = stroke)
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.width * .28f, center.y + kotlin.math.sin(angle).toFloat() * size.width * .28f)
                    val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.width * .38f, center.y + kotlin.math.sin(angle).toFloat() * size.width * .38f)
                    drawLine(color, start, end, stroke.width, StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.drawBottle(colors: IllustrationColors) {
    val w = size.width
    val h = size.height
    drawCircle(colors.soft.copy(alpha = .62f), w * .43f, Offset(w * .5f, h * .53f))
    drawRoundRect(colors.milk, Offset(w * .31f, h * .39f), Size(w * .38f, h * .44f), CornerRadius(w * .1f))
    drawRoundRect(colors.primary, Offset(w * .31f, h * .55f), Size(w * .38f, h * .28f), CornerRadius(w * .08f))
    drawRoundRect(colors.strong, Offset(w * .35f, h * .31f), Size(w * .3f, h * .12f), CornerRadius(w * .04f))
    val nipple =
        Path().apply {
            moveTo(w * .41f, h * .31f)
            quadraticTo(w * .44f, h * .12f, w * .5f, h * .12f)
            quadraticTo(w * .56f, h * .12f, w * .59f, h * .31f)
            close()
        }
    drawPath(nipple, colors.skin)
    repeat(3) { index ->
        val y = h * (.48f + index * .1f)
        drawLine(colors.line.copy(alpha = .58f), Offset(w * .55f, y), Offset(w * .64f, y), w * .018f, StrokeCap.Round)
    }
    drawCircle(colors.accent, w * .055f, Offset(w * .22f, h * .31f))
    drawCircle(colors.primary.copy(alpha = .65f), w * .035f, Offset(w * .78f, h * .47f))
    drawStar(colors.strong, Offset(w * .77f, h * .24f), w * .07f)
}

private fun DrawScope.drawDrops(colors: IllustrationColors) {
    val w = size.width
    val h = size.height
    drawCircle(colors.soft.copy(alpha = .62f), w * .43f, Offset(w * .47f, h * .53f))
    drawRoundRect(colors.milk, Offset(w * .26f, h * .35f), Size(w * .4f, h * .45f), CornerRadius(w * .1f))
    drawRoundRect(colors.primary, Offset(w * .26f, h * .55f), Size(w * .4f, h * .25f), CornerRadius(w * .08f))
    drawRoundRect(colors.strong, Offset(w * .3f, h * .23f), Size(w * .32f, h * .16f), CornerRadius(w * .04f))
    repeat(3) { index ->
        drawLine(colors.soft, Offset(w * (.35f + index * .09f), h * .25f), Offset(w * (.35f + index * .09f), h * .35f), w * .018f)
    }
    drawDrop(colors.primary, Offset(w * .77f, h * .47f), w * .09f)
    drawDrop(colors.accent, Offset(w * .82f, h * .67f), w * .055f)
    drawStar(colors.strong, Offset(w * .18f, h * .32f), w * .055f)
}

private fun DrawScope.drawExercise(colors: IllustrationColors) {
    val w = size.width
    val h = size.height
    drawRoundRect(colors.accent, Offset(w * .1f, h * .68f), Size(w * .8f, h * .13f), CornerRadius(w * .07f))
    drawOval(colors.skin, Offset(w * .28f, h * .42f), Size(w * .42f, h * .28f))
    drawCircle(colors.skin, w * .14f, Offset(w * .23f, h * .47f))
    drawArcRect(colors.strong, 200f, 140f, Rect(w * .14f, h * .39f, w * .31f, h * .55f), Stroke(w * .025f, cap = StrokeCap.Round))
    drawLine(colors.primary, Offset(w * .57f, h * .47f), Offset(w * .69f, h * .23f), w * .07f, StrokeCap.Round)
    drawLine(colors.primary, Offset(w * .66f, h * .48f), Offset(w * .81f, h * .31f), w * .07f, StrokeCap.Round)
    drawOval(colors.skin, Offset(w * .64f, h * .14f), Size(w * .18f, h * .1f))
    drawOval(colors.skin, Offset(w * .78f, h * .25f), Size(w * .17f, h * .1f))
    drawArcRect(colors.primary, 205f, 92f, Rect(w * .58f, h * .08f, w * .92f, h * .4f), Stroke(w * .025f, cap = StrokeCap.Round))
    drawCircle(colors.line, w * .012f, Offset(w * .19f, h * .45f))
    drawCircle(colors.line, w * .012f, Offset(w * .25f, h * .45f))
    drawArcRect(colors.line, 10f, 160f, Rect(w * .2f, h * .45f, w * .25f, h * .5f), Stroke(w * .012f))
}

private fun DrawScope.drawTummy(colors: IllustrationColors) {
    val w = size.width
    val h = size.height
    drawRoundRect(colors.accent, Offset(w * .08f, h * .7f), Size(w * .84f, h * .13f), CornerRadius(w * .07f))
    drawOval(colors.primary, Offset(w * .23f, h * .49f), Size(w * .48f, h * .24f))
    drawCircle(colors.skin, w * .15f, Offset(w * .7f, h * .42f))
    drawArcRect(colors.strong, 195f, 145f, Rect(w * .61f, h * .31f, w * .79f, h * .47f), Stroke(w * .025f, cap = StrokeCap.Round))
    drawLine(colors.skin, Offset(w * .58f, h * .62f), Offset(w * .78f, h * .69f), w * .065f, StrokeCap.Round)
    drawLine(colors.skin, Offset(w * .37f, h * .64f), Offset(w * .24f, h * .72f), w * .06f, StrokeCap.Round)
    drawCircle(colors.line, w * .013f, Offset(w * .73f, h * .41f))
    drawArcRect(colors.line, 30f, 125f, Rect(w * .7f, h * .42f, w * .76f, h * .48f), Stroke(w * .012f))
    drawCircle(colors.soft, w * .07f, Offset(w * .89f, h * .61f))
    drawStar(colors.strong, Offset(w * .89f, h * .61f), w * .045f)
    drawArcRect(colors.primary, 195f, 100f, Rect(w * .67f, h * .14f, w * .96f, h * .4f), Stroke(w * .025f, cap = StrokeCap.Round))
}

private fun DrawScope.drawJournal(colors: IllustrationColors) {
    val w = size.width
    val h = size.height
    drawCircle(colors.soft.copy(alpha = .65f), w * .43f, Offset(w * .5f, h * .5f))
    val left =
        Path().apply {
            moveTo(w * .17f, h * .27f)
            quadraticTo(w * .36f, h * .23f, w * .49f, h * .36f)
            lineTo(w * .49f, h * .78f)
            quadraticTo(w * .35f, h * .66f, w * .17f, h * .71f)
            close()
        }
    val right =
        Path().apply {
            moveTo(w * .51f, h * .36f)
            quadraticTo(w * .64f, h * .23f, w * .83f, h * .27f)
            lineTo(w * .83f, h * .71f)
            quadraticTo(w * .65f, h * .66f, w * .51f, h * .78f)
            close()
        }
    drawPath(left, colors.milk)
    drawPath(right, colors.accent)
    drawLine(colors.strong, Offset(w * .5f, h * .36f), Offset(w * .5f, h * .78f), w * .025f, StrokeCap.Round)
    drawHeart(colors.primary, Offset(w * .66f, h * .48f), w * .12f)
    drawLine(colors.line.copy(alpha = .55f), Offset(w * .25f, h * .43f), Offset(w * .42f, h * .43f), w * .015f)
    drawLine(colors.line.copy(alpha = .55f), Offset(w * .25f, h * .53f), Offset(w * .4f, h * .53f), w * .015f)
}

private fun DrawScope.drawDrop(
    color: Color,
    center: Offset,
    radius: Float,
) {
    val path =
        Path().apply {
            moveTo(center.x, center.y - radius * 1.5f)
            cubicTo(center.x + radius * 1.1f, center.y - radius * .2f, center.x + radius, center.y + radius, center.x, center.y + radius)
            cubicTo(center.x - radius, center.y + radius, center.x - radius * 1.1f, center.y - radius * .2f, center.x, center.y - radius * 1.5f)
            close()
        }
    drawPath(path, color)
}

private fun DrawScope.drawStar(
    color: Color,
    center: Offset,
    radius: Float,
) {
    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * .34f, StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), radius * .34f, StrokeCap.Round)
}

private fun DrawScope.drawStool(colors: IllustrationColors) {
    val w = size.width
    val h = size.height
    drawCircle(colors.soft.copy(alpha = .65f), w * .43f, Offset(w * .5f, h * .53f))
    val neutral = colors.line.copy(alpha = .82f)
    drawOval(neutral, Offset(w * .2f, h * .58f), Size(w * .6f, h * .25f))
    drawCircle(neutral, w * .22f, Offset(w * .42f, h * .57f))
    drawCircle(neutral, w * .18f, Offset(w * .62f, h * .58f))
    drawCircle(neutral, w * .13f, Offset(w * .52f, h * .35f))
    drawCircle(colors.milk, w * .026f, Offset(w * .42f, h * .6f))
    drawCircle(colors.milk, w * .026f, Offset(w * .59f, h * .6f))
    drawArcRect(
        colors.milk,
        15f,
        150f,
        Rect(w * .43f, h * .6f, w * .59f, h * .72f),
        Stroke(w * .025f, cap = StrokeCap.Round),
    )
    drawStar(colors.primary, Offset(w * .82f, h * .3f), w * .07f)
    drawCircle(colors.accent, w * .045f, Offset(w * .2f, h * .34f))
}

private fun DrawScope.drawArcRect(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    rect: Rect,
    stroke: Stroke,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = stroke,
    )
}

private fun DrawScope.drawHeart(
    color: Color,
    center: Offset,
    radius: Float,
) {
    val path =
        Path().apply {
            moveTo(center.x, center.y + radius)
            cubicTo(center.x - radius * 1.35f, center.y, center.x - radius, center.y - radius, center.x, center.y - radius * .25f)
            cubicTo(center.x + radius, center.y - radius, center.x + radius * 1.35f, center.y, center.x, center.y + radius)
            close()
        }
    drawPath(path, color)
}

private fun Color.luminance(): Float = (.2126f * red) + (.7152f * green) + (.0722f * blue)
