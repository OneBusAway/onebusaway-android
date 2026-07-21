package org.onebusaway.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The small set of Material Icons the app uses, vendored as Compose [ImageVector]s so the app no
 * longer depends on the deprecated (and frozen at 1.7.8) androidx.compose.material:material-icons-core
 * artifact. See #1963-series dependency cleanup.
 *
 * These are the classic **Material Icons** glyphs (the same design family the old `Icons.Filled.*` /
 * `Icons.AutoMirrored.Filled.*` rendered), exported from Google's official Compose icon export at
 * fonts.google.com. Path data is verbatim; the black [SolidColor] fill is tinted by the `Icon`
 * composable at the call site exactly as the library icons were. The two directional arrows carry
 * `autoMirror = true` to preserve RTL mirroring.
 *
 * To add or refresh an icon, re-export it from fonts.google.com (Material Icons family, 24dp) and
 * paste the path body here.
 */
object AppIcons {

    val Close: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "close") {
            moveTo(18.98f, 6.42f)
            lineTo(13.41f, 12f)
            lineToRelative(5.58f, 5.58f)
            lineToRelative(-1.41f, 1.41f)
            lineTo(12f, 13.41f)
            lineTo(6.42f, 18.98f)
            lineTo(5.02f, 17.58f)
            lineTo(10.59f, 12f)
            lineTo(5.02f, 6.42f)
            lineTo(6.42f, 5.02f)
            lineTo(12f, 10.59f)
            lineTo(17.58f, 5.02f)
            lineToRelative(1.41f, 1.41f)
            close()
        }
    }

    val Clear: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "clear") {
            moveTo(18.98f, 6.42f)
            lineTo(13.41f, 12f)
            lineToRelative(5.58f, 5.58f)
            lineToRelative(-1.41f, 1.41f)
            lineTo(12f, 13.41f)
            lineTo(6.42f, 18.98f)
            lineTo(5.02f, 17.58f)
            lineTo(10.59f, 12f)
            lineTo(5.02f, 6.42f)
            lineTo(6.42f, 5.02f)
            lineTo(12f, 10.59f)
            lineTo(17.58f, 5.02f)
            lineToRelative(1.41f, 1.41f)
            close()
        }
    }

    val Info: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "info") {
            moveTo(12.98f, 9f)
            verticalLineTo(6.98f)
            horizontalLineTo(11.02f)
            verticalLineTo(9f)
            horizontalLineToRelative(1.97f)
            close()
            moveToRelative(0f, 8.02f)
            verticalLineToRelative(-6f)
            horizontalLineTo(11.02f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(1.97f)
            close()
            moveTo(12f, 2.02f)
            quadToRelative(4.13f, 0f, 7.05f, 2.93f)
            reflectiveQuadTo(21.98f, 12f)
            reflectiveQuadToRelative(-2.93f, 7.05f)
            reflectiveQuadTo(12f, 21.98f)
            reflectiveQuadTo(4.95f, 19.05f)
            reflectiveQuadTo(2.02f, 12f)
            reflectiveQuadTo(4.95f, 4.95f)
            reflectiveQuadTo(12f, 2.02f)
            close()
        }
    }

    val KeyboardArrowDown: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "keyboard_arrow_down") {
            moveTo(7.41f, 8.58f)
            lineTo(12f, 13.17f)
            lineTo(16.59f, 8.58f)
            lineTo(18f, 9.98f)
            lineToRelative(-6f, 6f)
            lineToRelative(-6f, -6f)
            lineTo(7.41f, 8.58f)
            close()
        }
    }

    val KeyboardArrowUp: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "keyboard_arrow_up") {
            moveTo(7.41f, 15.42f)
            lineTo(6f, 14.02f)
            lineToRelative(6f, -6f)
            lineToRelative(6f, 6f)
            lineToRelative(-1.41f, 1.41f)
            lineTo(12f, 10.83f)
            lineTo(7.41f, 15.42f)
            close()
        }
    }

    val MoreVert: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "more_vert") {
            moveTo(12f, 15.98f)
            quadToRelative(0.8f, 0f, 1.41f, 0.61f)
            reflectiveQuadTo(14.02f, 18f)
            reflectiveQuadToRelative(-0.61f, 1.41f)
            reflectiveQuadTo(12f, 20.02f)
            reflectiveQuadTo(10.59f, 19.41f)
            reflectiveQuadTo(9.98f, 18f)
            reflectiveQuadToRelative(0.61f, -1.41f)
            reflectiveQuadTo(12f, 15.98f)
            close()
            moveToRelative(0f, -6f)
            quadToRelative(0.8f, 0f, 1.41f, 0.61f)
            reflectiveQuadTo(14.02f, 12f)
            reflectiveQuadToRelative(-0.61f, 1.41f)
            reflectiveQuadTo(12f, 14.02f)
            reflectiveQuadTo(10.59f, 13.41f)
            reflectiveQuadTo(9.98f, 12f)
            reflectiveQuadToRelative(0.61f, -1.41f)
            reflectiveQuadTo(12f, 9.98f)
            close()
            moveTo(12f, 8.02f)
            quadToRelative(-0.8f, 0f, -1.41f, -0.61f)
            reflectiveQuadTo(9.98f, 6f)
            reflectiveQuadTo(10.59f, 4.59f)
            reflectiveQuadTo(12f, 3.98f)
            reflectiveQuadToRelative(1.41f, 0.61f)
            reflectiveQuadTo(14.02f, 6f)
            reflectiveQuadTo(13.41f, 7.41f)
            reflectiveQuadTo(12f, 8.02f)
            close()
        }
    }

    val Settings: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "settings") {
            moveTo(12f, 15.52f)
            quadToRelative(1.45f, 0f, 2.48f, -1.03f)
            reflectiveQuadTo(15.52f, 12f)
            reflectiveQuadTo(14.48f, 9.52f)
            reflectiveQuadTo(12f, 8.48f)
            reflectiveQuadTo(9.52f, 9.52f)
            reflectiveQuadTo(8.48f, 12f)
            reflectiveQuadToRelative(1.03f, 2.48f)
            reflectiveQuadTo(12f, 15.52f)
            close()
            moveToRelative(7.45f, -2.53f)
            lineToRelative(2.11f, 1.64f)
            quadToRelative(0.33f, 0.23f, 0.09f, 0.66f)
            lineToRelative(-2.02f, 3.47f)
            quadToRelative(-0.19f, 0.33f, -0.61f, 0.19f)
            lineTo(16.55f, 17.95f)
            quadToRelative(-0.98f, 0.7f, -1.69f, 0.98f)
            lineToRelative(-0.38f, 2.63f)
            quadToRelative(-0.09f, 0.42f, -0.47f, 0.42f)
            horizontalLineTo(9.98f)
            quadToRelative(-0.38f, 0f, -0.47f, -0.42f)
            lineTo(9.14f, 18.94f)
            quadTo(8.25f, 18.56f, 7.45f, 17.95f)
            lineTo(4.97f, 18.94f)
            quadTo(4.55f, 19.08f, 4.36f, 18.75f)
            lineTo(2.34f, 15.28f)
            quadTo(2.11f, 14.86f, 2.44f, 14.63f)
            lineTo(4.55f, 12.98f)
            quadTo(4.5f, 12.66f, 4.5f, 12f)
            reflectiveQuadTo(4.55f, 11.02f)
            lineTo(2.44f, 9.38f)
            quadTo(2.11f, 9.14f, 2.34f, 8.72f)
            lineTo(4.36f, 5.25f)
            quadTo(4.55f, 4.92f, 4.97f, 5.06f)
            lineTo(7.45f, 6.05f)
            quadTo(8.44f, 5.34f, 9.14f, 5.06f)
            lineTo(9.52f, 2.44f)
            quadTo(9.61f, 2.02f, 9.98f, 2.02f)
            horizontalLineToRelative(4.03f)
            quadToRelative(0.38f, 0f, 0.47f, 0.42f)
            lineToRelative(0.38f, 2.63f)
            quadToRelative(0.89f, 0.38f, 1.69f, 0.98f)
            lineTo(19.03f, 5.06f)
            quadToRelative(0.42f, -0.14f, 0.61f, 0.19f)
            lineToRelative(2.02f, 3.47f)
            quadToRelative(0.23f, 0.42f, -0.09f, 0.66f)
            lineToRelative(-2.11f, 1.64f)
            quadTo(19.5f, 11.34f, 19.5f, 12f)
            reflectiveQuadToRelative(-0.05f, 0.98f)
            close()
        }
    }

    val Search: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "search") {
            moveTo(9.52f, 14.02f)
            quadToRelative(1.88f, 0f, 3.19f, -1.31f)
            reflectiveQuadTo(14.02f, 9.52f)
            reflectiveQuadTo(12.7f, 6.33f)
            reflectiveQuadTo(9.52f, 5.02f)
            reflectiveQuadTo(6.33f, 6.33f)
            reflectiveQuadTo(5.02f, 9.52f)
            reflectiveQuadTo(6.33f, 12.7f)
            reflectiveQuadToRelative(3.19f, 1.31f)
            close()
            moveToRelative(6f, 0f)
            lineToRelative(4.97f, 4.97f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(14.02f, 15.52f)
            verticalLineToRelative(-0.8f)
            lineTo(13.73f, 14.44f)
            quadToRelative(-1.78f, 1.55f, -4.22f, 1.55f)
            quadTo(6.8f, 15.98f, 4.9f, 14.11f)
            reflectiveQuadTo(3f, 9.52f)
            reflectiveQuadTo(4.9f, 4.9f)
            reflectiveQuadTo(9.52f, 3f)
            reflectiveQuadToRelative(4.59f, 1.9f)
            reflectiveQuadToRelative(1.88f, 4.62f)
            quadToRelative(0f, 0.98f, -0.47f, 2.23f)
            reflectiveQuadToRelative(-1.08f, 1.99f)
            lineToRelative(0.28f, 0.28f)
            horizontalLineToRelative(0.8f)
            close()
        }
    }

    val Place: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "place") {
            moveTo(12f, 11.48f)
            quadToRelative(1.03f, 0f, 1.76f, -0.73f)
            reflectiveQuadTo(14.48f, 9f)
            reflectiveQuadTo(13.76f, 7.24f)
            reflectiveQuadTo(12f, 6.52f)
            reflectiveQuadTo(10.24f, 7.24f)
            reflectiveQuadTo(9.52f, 9f)
            reflectiveQuadToRelative(0.73f, 1.76f)
            reflectiveQuadTo(12f, 11.48f)
            close()
            moveTo(12f, 2.02f)
            quadToRelative(2.91f, 0f, 4.95f, 2.04f)
            reflectiveQuadTo(18.98f, 9f)
            quadToRelative(0f, 1.45f, -0.73f, 3.33f)
            reflectiveQuadTo(16.5f, 15.84f)
            reflectiveQuadToRelative(-2.04f, 3.07f)
            reflectiveQuadToRelative(-1.71f, 2.27f)
            lineTo(12f, 21.98f)
            quadTo(11.72f, 21.66f, 11.25f, 21.12f)
            reflectiveQuadTo(9.56f, 18.96f)
            reflectiveQuadTo(7.43f, 15.82f)
            reflectiveQuadTo(5.77f, 12.38f)
            reflectiveQuadTo(5.02f, 9f)
            quadToRelative(0f, -2.91f, 2.04f, -4.95f)
            reflectiveQuadTo(12f, 2.02f)
            close()
        }
    }

    val Menu: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "menu") {
            moveTo(3f, 6f)
            horizontalLineTo(21f)
            verticalLineTo(8.02f)
            horizontalLineTo(3f)
            verticalLineTo(6f)
            close()
            moveToRelative(0f, 6.98f)
            verticalLineTo(11.02f)
            horizontalLineTo(21f)
            verticalLineToRelative(1.97f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 18f)
            verticalLineTo(15.98f)
            horizontalLineTo(21f)
            verticalLineTo(18f)
            horizontalLineTo(3f)
            close()
        }
    }

    val ArrowBack: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "arrow_back", autoMirror = true) {
            moveTo(20.02f, 11.02f)
            verticalLineToRelative(1.97f)
            horizontalLineTo(7.83f)
            lineToRelative(5.58f, 5.63f)
            lineTo(12f, 20.02f)
            lineTo(3.98f, 12f)
            lineTo(12f, 3.98f)
            lineToRelative(1.41f, 1.41f)
            lineTo(7.83f, 11.02f)
            horizontalLineTo(20.02f)
            close()
        }
    }

    val ArrowForward: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        materialIcon(name = "arrow_forward", autoMirror = true) {
            moveTo(12f, 3.98f)
            lineTo(20.02f, 12f)
            lineTo(12f, 20.02f)
            lineTo(10.59f, 18.61f)
            lineToRelative(5.58f, -5.63f)
            horizontalLineTo(3.98f)
            verticalLineTo(11.02f)
            horizontalLineTo(16.17f)
            lineTo(10.59f, 5.39f)
            lineTo(12f, 3.98f)
            close()
        }
    }
}

private inline fun materialIcon(
    name: String,
    autoMirror: Boolean = false,
    pathBody: PathBuilder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero, pathBuilder = pathBody)
}.build()
