package com.example.earnitv2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EarnItAppIcon(
    packageName: String?,
    appName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        packageName?.takeIf { it.isNotBlank() }?.let { safePackage ->
            EarnItAppIconCache.get(safePackage) {
                context.packageManager.getApplicationIcon(safePackage).toIconBitmap()
            }
        }
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = appName,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        EarnItAppInitialTile(appName = appName, size = size, modifier = modifier)
    }
}

@Composable
fun EarnItAppInitialTile(
    appName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private object EarnItAppIconCache {
    private val cache = object : LruCache<String, Bitmap>(80) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun get(packageName: String, loader: () -> Bitmap): Bitmap? {
        cache.get(packageName)?.let { return it }
        return runCatching { loader() }.getOrNull()?.also { bitmap ->
            cache.put(packageName, bitmap)
        }
    }
}

private fun Drawable.toIconBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.toPickerSizedBitmap()

    val width = intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(PICKER_ICON_MAX_PX) ?: PICKER_ICON_MAX_PX
    val height = intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(PICKER_ICON_MAX_PX) ?: PICKER_ICON_MAX_PX
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun Bitmap.toPickerSizedBitmap(): Bitmap {
    if (width <= PICKER_ICON_MAX_PX && height <= PICKER_ICON_MAX_PX) return this
    val scale = minOf(PICKER_ICON_MAX_PX.toFloat() / width, PICKER_ICON_MAX_PX.toFloat() / height)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private const val PICKER_ICON_MAX_PX = 96
