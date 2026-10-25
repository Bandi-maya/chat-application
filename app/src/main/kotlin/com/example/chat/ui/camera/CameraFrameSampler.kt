package com.example.chat.ui.camera

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Exact port of the frame-sampling half of camera_face_detection_bridge.dart:
 *  _toRgbThumbnail, _unrotate, _readNv21, _readBgra, _readYuv.
 *
 *  Pure JVM logic, unit-tested. Produces the same low-bandwidth RGB thumbnail
 *  (long side 160, even width) the Dart bridge feeds its detector; the Android
 *  detector wrapper (FaceDetectionBridge, ML Kit) consumes [RgbFrame] so the
 *  on-device/privacy contract is preserved verbatim.
 *
 *  Input frames are normalized to [FrameData] so both CameraX ImageProxy
 *  (YUV_420_888, 3 planes) and any single-plane NV21/BGRA source fit the same
 *  code path the Dart used with camera package planes. */

data class FramePlane(
    val bytes: ByteArray,
    val bytesPerRow: Int,
    val bytesPerPixel: Int,
)

enum class FrameFormat { yuv420, nv21, bgra8888 }

data class FrameData(
    val width: Int,
    val height: Int,
    val format: FrameFormat,
    val planes: List<FramePlane>,
)

data class RgbFrame(val width: Int, val height: Int, val bytes: ByteArray)

object CameraFrameSampler {
    const val LONG_SIDE = 160

    /** Port of _toRgbThumbnail: orient-aware downscale to an RGB888 buffer.
     *  Width rounded down to even (`& ~1`), height >= 1, sampling at pixel
     *  centers, source coords un-rotated into sensor space. */
    fun toRgbThumbnail(image: FrameData, sensorOrientation: Int): RgbFrame {
        val rotate = sensorOrientation % 180 != 0
        val orientedWidth = if (rotate) image.height else image.width
        val orientedHeight = if (rotate) image.width else image.height
        val scale = min(1.0, LONG_SIDE.toDouble() / max(orientedWidth, orientedHeight))
        val width = max(2, (orientedWidth * scale).roundToInt() and -2)
        val height = max(1, (orientedHeight * scale).roundToInt())
        val pixels = ByteArray(width * height * 3)

        require(image.format == FrameFormat.yuv420 || image.format == FrameFormat.nv21 ||
            image.format == FrameFormat.bgra8888) {
            "Unsupported camera frame format: ${image.format}"
        }
        check(image.planes.isNotEmpty()) { "Camera frame has no image planes." }

        val yPlane = image.planes[0]
        val uvPlane = image.planes.getOrNull(1)
        val vPlane = image.planes.getOrNull(2)

        for (y in 0 until height) {
            val orientedY = min(orientedHeight - 1, ((y + 0.5) * orientedHeight / height).toInt())
            for (x in 0 until width) {
                val orientedX = min(orientedWidth - 1, ((x + 0.5) * orientedWidth / width).toInt())
                val (sx, sy) = unrotate(orientedX, orientedY, image.width, image.height, sensorOrientation)
                val rgb = when (image.format) {
                    FrameFormat.bgra8888 -> readBgra(yPlane, sx, sy)
                    FrameFormat.nv21 -> readNv21(yPlane, image.width, image.height, sx, sy)
                    FrameFormat.yuv420 -> readYuv(yPlane, uvPlane, vPlane, sx, sy)
                }
                val index = (y * width + x) * 3
                pixels[index] = rgb[0].toByte()
                pixels[index + 1] = rgb[1].toByte()
                pixels[index + 2] = rgb[2].toByte()
            }
        }
        return RgbFrame(width = width, height = height, bytes = pixels)
    }

    /** Port of _unrotate: oriented coords -> sensor coords. */
    fun unrotate(x: Int, y: Int, width: Int, height: Int, sensorOrientation: Int): Pair<Int, Int> {
        return when ((sensorOrientation % 360 + 360) % 360) {
            90 -> y.coerceIn(0, width - 1) to (height - 1 - x).coerceIn(0, height - 1)
            180 -> (width - 1 - x).coerceIn(0, width - 1) to (height - 1 - y).coerceIn(0, height - 1)
            270 -> (width - 1 - y).coerceIn(0, width - 1) to x.coerceIn(0, height - 1)
            else -> x.coerceIn(0, width - 1) to y.coerceIn(0, height - 1)
        }
    }

    /** Port of _readNv21 (single-plane NV21: Y rows then interleaved VU). */
    fun readNv21(plane: FramePlane, width: Int, height: Int, x: Int, y: Int): IntArray {
        val bytes = plane.bytes
        val yIndex = y * plane.bytesPerRow + x
        val uvBase = plane.bytesPerRow * height
        val uvIndex = uvBase + (y shr 1) * plane.bytesPerRow + (x and -2)
        val luminance = bytes[yIndex.coerceIn(0, bytes.size - 1)].toInt() and 0xFF
        var v = 128.0
        var u = 128.0
        if (uvIndex + 1 < bytes.size) {
            v = (bytes[uvIndex].toInt() and 0xFF).toDouble()
            u = (bytes[uvIndex + 1].toInt() and 0xFF).toDouble()
        }
        return yuvToRgb(luminance.toDouble(), u, v)
    }

    /** Port of _readBgra. */
    fun readBgra(plane: FramePlane, x: Int, y: Int): IntArray {
        val stride = plane.bytesPerPixel
        val index = y * plane.bytesPerRow + x * stride
        val bytes = plane.bytes
        return intArrayOf(
            bytes[(index + 2).coerceIn(0, bytes.size - 1)].toInt() and 0xFF,
            bytes[(index + 1).coerceIn(0, bytes.size - 1)].toInt() and 0xFF,
            bytes[index.coerceIn(0, bytes.size - 1)].toInt() and 0xFF,
        )
    }

    /** Port of _readYuv (3-plane I420, or 2-plane interleaved NV12 U/V). */
    fun readYuv(yPlane: FramePlane, uvPlane: FramePlane?, vPlane: FramePlane?, x: Int, y: Int): IntArray {
        val yIndex = y * yPlane.bytesPerRow + x * yPlane.bytesPerPixel
        val luminance = (yPlane.bytes[yIndex.coerceIn(0, yPlane.bytes.size - 1)].toInt() and 0xFF).toDouble()
        var u = 128.0
        var v = 128.0

        if (uvPlane != null && vPlane != null) {
            val uIndex = (y shr 1) * uvPlane.bytesPerRow + (x shr 1) * uvPlane.bytesPerPixel
            val vIndex = (y shr 1) * vPlane.bytesPerRow + (x shr 1) * vPlane.bytesPerPixel
            u = (uvPlane.bytes[uIndex.coerceIn(0, uvPlane.bytes.size - 1)].toInt() and 0xFF).toDouble()
            v = (vPlane.bytes[vIndex.coerceIn(0, vPlane.bytes.size - 1)].toInt() and 0xFF).toDouble()
        } else if (uvPlane != null) {
            val uvIndex = (y shr 1) * uvPlane.bytesPerRow + (x shr 1) * uvPlane.bytesPerPixel
            if (uvIndex + 1 < uvPlane.bytes.size) {
                // Two-plane camera buffers use interleaved U/V (NV12) samples.
                u = (uvPlane.bytes[uvIndex].toInt() and 0xFF).toDouble()
                v = (uvPlane.bytes[uvIndex + 1].toInt() and 0xFF).toDouble()
            }
        }
        return yuvToRgb(luminance, u, v)
    }

    /** Shared BT.601 conversion used by all three readers in the Dart. */
    private fun yuvToRgb(luminance: Double, u: Double, v: Double): IntArray {
        val red = (luminance + 1.402 * (v - 128)).roundToInt().coerceIn(0, 255)
        val green = (luminance - 0.344136 * (u - 128) - 0.714136 * (v - 128)).roundToInt().coerceIn(0, 255)
        val blue = (luminance + 1.772 * (u - 128)).roundToInt().coerceIn(0, 255)
        return intArrayOf(red, green, blue)
    }
}
