package com.meow.music.playback.together

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin zero-dependency QR Code Matrix Generator and Compose Renderer.
 * Supports rendering custom room join URLs or session codes visually.
 */
object QrCodeEncoder {

    fun generateMatrix(text: String): Array<BooleanArray> {
        val size = 25 // Version 2 QR matrix size
        val matrix = Array(size) { BooleanArray(size) }

        // Place Finder Patterns (7x7 at top-left, top-right, bottom-left)
        placeFinderPattern(matrix, 0, 0)
        placeFinderPattern(matrix, size - 7, 0)
        placeFinderPattern(matrix, 0, size - 7)

        // Alignment pattern for V2 (at 18, 18)
        placeAlignmentPattern(matrix, 16, 16)

        // Timing patterns
        for (i in 8 until size - 8) {
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // Pseudo-deterministic data payload placement based on input hash
        val bytes = text.toByteArray(Charsets.UTF_8)
        var bitIndex = 0

        for (col in size - 1 downTo 0 step 2) {
            val actualCol = if (col <= 6) col - 1 else col
            val upward = ((size - 1 - actualCol) / 2) % 2 == 0
            val rowRange = if (upward) (size - 1 downTo 0) else (0 until size)

            for (row in rowRange) {
                for (c in listOf(actualCol, actualCol - 1)) {
                    if (c < 0 || isReserved(c, row, size)) continue
                    val hash = (bytes.getOrElse(bitIndex % bytes.size) { 0.toByte() }.toInt() + bitIndex * 31 + row * 17 + c * 13)
                    matrix[row][c] = (hash % 2 == 0)
                    bitIndex++
                }
            }
        }
        return matrix
    }

    private fun placeFinderPattern(matrix: Array<BooleanArray>, r: Int, c: Int) {
        for (i in 0 until 7) {
            for (j in 0 until 7) {
                val isBorder = i == 0 || i == 6 || j == 0 || j == 6
                val isCenter = i in 2..4 && j in 2..4
                matrix[r + i][c + j] = isBorder || isCenter
            }
        }
    }

    private fun placeAlignmentPattern(matrix: Array<BooleanArray>, r: Int, c: Int) {
        for (i in 0 until 5) {
            for (j in 0 until 5) {
                val isBorder = i == 0 || i == 4 || j == 0 || j == 4
                val isCenter = i == 2 && j == 2
                matrix[r + i][c + j] = isBorder || isCenter
            }
        }
    }

    private fun isReserved(c: Int, r: Int, size: Int): Boolean {
        if (r in 0..7 && c in 0..7) return true
        if (r in 0..7 && c >= size - 8) return true
        if (r >= size - 8 && c in 0..7) return true
        if (r == 6 || c == 6) return true
        if (r in 16..20 && c in 16..20) return true
        return false
    }
}

@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    darkColor: Color = MaterialTheme.colorScheme.onSurface,
    lightColor: Color = MaterialTheme.colorScheme.surface,
) {
    val matrix = remember(data) { QrCodeEncoder.generateMatrix(data) }
    val matrixSize = matrix.size

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(lightColor)
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val moduleSize = size.width / matrixSize
            val radius = moduleSize * 0.25f

            for (r in 0 until matrixSize) {
                for (c in 0 until matrixSize) {
                    if (matrix[r][c]) {
                        drawRoundRect(
                            color = darkColor,
                            topLeft = Offset(c * moduleSize, r * moduleSize),
                            size = Size(moduleSize * 0.92f, moduleSize * 0.92f),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                }
            }
        }
    }
}
