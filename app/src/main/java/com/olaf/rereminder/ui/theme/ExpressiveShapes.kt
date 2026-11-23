package com.olaf.rereminder.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * The few Material 3 Expressive shapes the app uses, built from the same polygon maths
 * (`androidx.graphics.shapes`): a scalloped cookie for what is chosen or running, a sunny burst
 * for what is done. Used sparingly — on a selection, a status, the ringing alarm — so they mark
 * a moment instead of decorating every surface.
 *
 * All polygons are normalised to the unit square, so a shape fills whatever box it clips.
 */
object ExpressiveShapes {
    val Circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 9).normalized()

    /** Nine soft scallops: something picked, or a reminder that is running. */
    val Cookie: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.8f,
        rounding = CornerRounding(radius = 0.15f),
    ).normalized()

    /** Eight short rays: a step that is done. */
    val Sunny: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 8,
        innerRadius = 0.83f,
        rounding = CornerRounding(radius = 0.1f),
    ).normalized()

    val CircleToCookie = Morph(Circle, Cookie)
    val CircleToSunny = Morph(Circle, Sunny)
}

/** Clips to [polygon], turned by [rotation] degrees about its centre. */
class PolygonShape(
    private val polygon: RoundedPolygon,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(fit(polygon.toPath(), size, rotation).asComposePath())

    override fun equals(other: Any?): Boolean =
        other is PolygonShape && other.polygon === polygon && other.rotation == rotation

    override fun hashCode(): Int = 31 * System.identityHashCode(polygon) + rotation.hashCode()
}

/**
 * Clips to [morph] at [progress] — 0 its start shape, 1 its end — so a circle can grow its
 * scallops as an item is selected, instead of swapping one shape for another.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(fit(morph.toPath(progress), size, rotation).asComposePath())

    override fun equals(other: Any?): Boolean =
        other is MorphShape && other.morph === morph && other.progress == progress && other.rotation == rotation

    override fun hashCode(): Int =
        (31 * System.identityHashCode(morph) + progress.hashCode()) * 31 + rotation.hashCode()
}

/** Scales a unit-square path to [size], turning it about the centre first. */
private fun fit(path: android.graphics.Path, size: Size, rotation: Float): android.graphics.Path {
    val matrix = android.graphics.Matrix()
    if (rotation != 0f) matrix.postRotate(rotation, 0.5f, 0.5f)
    matrix.postScale(size.width, size.height)
    path.transform(matrix)
    return path
}
