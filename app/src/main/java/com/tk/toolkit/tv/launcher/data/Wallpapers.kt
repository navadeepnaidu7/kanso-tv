package com.tk.toolkit.tv.launcher.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/** Where the home view's wallpaper comes from. */
enum class WallpaperSource(val title: String, val description: String) {
    Art("Japanese art", "Hokusai and Hiroshige woodblock prints"),
    Earth("Earth", "Photos of Earth from the International Space Station"),
    Mixed("Art and Earth", "Both, shuffled together"),
    Mine("My images", "Photos on this TV, in Pictures or Downloads"),
    Off("Off", "The focused app's artwork, blurred"),
}

/** One wallpaper: a curated image downloaded on first use, or a photo already on the TV. */
data class Wallpaper(val key: String, val title: String, val credit: String, val url: String? = null, val uri: Uri? = null)

/**
 * Curated, public-domain sets: woodblock prints from The Met's Open Access collection (CC0), and
 * Earth photography from NASA's image library.
 */
object WallpaperCatalog {

    private fun art(id: Int, title: String, artist: String, path: String) = Wallpaper(
        key = "met-$id",
        title = title,
        credit = "$artist · The Metropolitan Museum of Art",
        url = "https://images.metmuseum.org/CRDImages/$path",
    )

    private fun earth(id: String, title: String) = Wallpaper(
        key = "nasa-$id",
        title = title,
        credit = "NASA · International Space Station",
        url = "https://images-assets.nasa.gov/image/$id/$id~large.jpg",
    )

    val Art = listOf(
        art(45434, "The Great Wave off Kanagawa", "Hokusai", "as/original/DP130155.jpg"),
        art(36490, "Red Fuji (South Wind, Clear Sky)", "Hokusai", "as/original/DP141062.jpg"),
        art(56229, "Storm below Mount Fuji", "Hokusai", "as/original/DP141054.jpg"),
        art(56389, "Lower Meguro", "Hokusai", "as/original/DP141086.jpg"),
        art(54868, "Reflection in Lake at Misaka in Kai Province", "Hokusai", "as/original/DP141064.jpg"),
        art(56686, "Fuji—The Tama River, Musashi Province", "Hokusai", "as/original/DP140975.jpg"),
        art(55735, "Ejiri in Suruga Province", "Hokusai", "as/original/DP141046.jpg"),
        art(55741, "Tsukudajima in Musashi Province", "Hokusai", "as/original/DP141089.jpg"),
        art(56395, "Surugadai in Edo", "Hokusai", "as/original/DP141094.jpg"),
        art(56787, "Fujimigahara in Owari Province", "Hokusai", "as/original/DP141033.jpg"),
        art(36506, "In the Mountains of Tōtomi Province", "Hokusai", "as/original/DP140988.jpg"),
        art(56727, "Kajikazawa in Kai Province", "Hokusai", "as/original/DP140973.jpg"),
        art(56384, "Nakahara in Sagami Province", "Hokusai", "as/original/DP141073.jpg"),
        art(36521, "Sudden Shower at Shōno", "Hiroshige", "as/original/DP122216.jpg"),
        art(36971, "Spring Rain at Tsuchiyama", "Hiroshige", "as/original/DP122230.jpg"),
        art(37208, "Hara", "Hiroshige", "as/original/DP122894.jpg"),
        art(55685, "Fujikawa", "Hiroshige", "as/original/DP122810.jpg"),
        art(55697, "Tsuchiyama", "Hiroshige", "as/original/DP122822.jpg"),
        art(36556, "Mishima", "Hiroshige", "as/original/DP122835.jpg"),
        art(55696, "Sakanoshita", "Hiroshige", "as/original/DP122821.jpg"),
        art(37213, "Ejiri", "Hiroshige", "as/original/DP122899.jpg"),
        art(55989, "Numazu", "Hiroshige", "as/original/DP123231.jpg"),
        art(56915, "Evening Snow at Kanbara", "Hiroshige", "as/original/DP109333.jpg"),
    )

    val Earth = listOf(
        earth("iss044e092267", "Clouds over the ocean"),
        earth("iss044e092246", "Earth's horizon"),
        earth("iss043e072717", "City lights at night"),
        earth("iss047e141893", "Sunlight on the sea"),
        earth("iss066e146457", "Aurora from orbit"),
        earth("iss064e023201", "City lights at night"),
        earth("iss072e451060", "Aurora from orbit"),
        earth("iss067e269019", "Aurora from orbit"),
        earth("iss072e159516", "Aurora over the horizon"),
        earth("iss072e820937", "A cyclone from orbit"),
        earth("iss028e007274", "Sunrise on the horizon"),
        earth("iss069e005471", "The Eye of the Sahara"),
        earth("iss071e082709", "Desert lake"),
        earth("iss071e082698", "Desert coastline"),
        earth("iss067e286458", "Desert from orbit"),
        earth("iss072e065377", "Sand dunes"),
        earth("iss066e137392", "Red dunes"),
        earth("iss070e052305", "Sahara from orbit"),
        earth("iss074e0603582", "Snow-capped mountains"),
        earth("iss070e091704", "Snowy ridges"),
        earth("iss066e141209", "Mountain range"),
        earth("iss069e060266", "Glacier"),
        earth("iss008e13304", "Snow-capped mountains"),
        earth("iss026e015208", "Glaciers"),
    )
}

/** Lists and loads wallpapers: downloads curated images once into app storage, reads the user's own from MediaStore. */
class WallpaperRepository(private val context: Context) {

    private companion object {
        const val TAG = "Wallpapers"
    }

    private val cacheDir = File(context.filesDir, "wallpapers").apply { mkdirs() }

    /** The wallpapers for [source], shuffled. Empty when there are none (or no photo permission). */
    fun list(source: WallpaperSource): List<Wallpaper> = when (source) {
        WallpaperSource.Art -> WallpaperCatalog.Art.shuffled()
        WallpaperSource.Earth -> WallpaperCatalog.Earth.shuffled()
        WallpaperSource.Mixed -> (WallpaperCatalog.Art + WallpaperCatalog.Earth).shuffled()
        WallpaperSource.Mine -> myImages().shuffled()
        WallpaperSource.Off -> emptyList()
    }

    /** Images in Pictures/ and Download/ (where LocalSend saves), newest first. */
    fun myImages(): List<Wallpaper> = runCatching {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf("Pictures/%", "Download/%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(id))
                    add(Wallpaper("mine-${cursor.getLong(id)}", cursor.getString(name).substringBeforeLast('.'), "My images", uri = uri))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    /**
     * The wallpaper ready to draw at [width]x[height]: cropped to fill, or, for tall images,
     * centered over a blurred fill of itself; with soft scrims baked in for the clock and dock.
     * Blocking (network and decoding), so call it off the main thread. Null if it can't be had.
     */
    fun load(wallpaper: Wallpaper, width: Int, height: Int): ImageBitmap? = runCatching {
        val source = decode(wallpaper, width, height)
            ?: return null.also { Log.w(TAG, "Couldn't decode wallpaper ${wallpaper.key}") }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val imageAspect = source.width.toFloat() / source.height
        val screenAspect = width.toFloat() / height
        if (imageAspect >= 1.2f) {
            val crop = cropToFill(source.width, source.height, screenAspect)
            // Museum scans include the print's paper margin; trim it off the edges.
            if (wallpaper.key.startsWith("met-")) crop.inset((crop.width() * 0.03f).toInt(), (crop.height() * 0.03f).toInt())
            canvas.drawBitmap(source, crop, Rect(0, 0, width, height), paint)
        } else {
            // Tall or square: a soft, dimmed fill of the image itself, with the whole image centered on it.
            val tiny = Bitmap.createScaledBitmap(source, 24, max(1, (24 / imageAspect).toInt()), true)
            canvas.drawBitmap(tiny, cropToFill(tiny.width, tiny.height, screenAspect), Rect(0, 0, width, height), paint)
            canvas.drawColor(0x80000000.toInt())
            val h = height * 0.92f
            val w = h * imageAspect
            canvas.drawBitmap(source, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), paint)
        }
        bakeScrims(canvas, width.toFloat(), height.toFloat())
        out.asImageBitmap()
    }.onFailure { Log.w(TAG, "Couldn't load wallpaper ${wallpaper.key}", it) }.getOrNull()

    private fun decode(wallpaper: Wallpaper, width: Int, height: Int): Bitmap? {
        wallpaper.uri?.let { uri ->
            return decodeSampled(width, height) { context.contentResolver.openInputStream(uri) }
        }
        val file = File(cacheDir, "${wallpaper.key}.jpg")
        if (!file.exists()) download(wallpaper.url ?: return null, file, width, height)
        return decodeSampled(width, height) { file.inputStream() }
    }

    /** Downloads [url], shrinks it to about screen size and saves it, so the full original is fetched only once. */
    private fun download(url: String, file: File, width: Int, height: Int) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Toolkit-TV")
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        val temp = File(file.parentFile, "${file.name}.part")
        try {
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            val bitmap = decodeSampled(width, height) { temp.inputStream() } ?: return
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally {
            temp.delete()
            connection.disconnect()
        }
    }

    /** Decodes at the smallest power-of-two downsample that still covers [width]x[height]. */
    private fun decodeSampled(width: Int, height: Int, open: () -> java.io.InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; the size lands in [bounds].
        (open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open()?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun cropToFill(w: Int, h: Int, targetAspect: Float): Rect {
        val aspect = w.toFloat() / h
        return if (aspect > targetAspect) {
            val cw = (h * targetAspect).toInt()
            Rect((w - cw) / 2, 0, (w + cw) / 2, h)
        } else {
            val ch = (w / targetAspect).toInt()
            Rect(0, (h - ch) / 2, w, (h + ch) / 2)
        }
    }

    /** Darkens the top (behind the clock) and bottom (behind the dock) so text and tiles stay legible. */
    private fun bakeScrims(canvas: Canvas, w: Float, h: Float) {
        fun shade(alpha: Float) = android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0)
        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.4f, shade(0.62f), 0, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.4f, paint)
        // The clock and caption sit top left, so that corner gets a little extra.
        paint.shader = android.graphics.RadialGradient(0f, 0f, w * 0.4f, shade(0.35f), 0, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w * 0.4f, w * 0.4f, paint)
        paint.shader = LinearGradient(0f, h * 0.45f, 0f, h, 0, shade(0.72f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.45f, w, h, paint)
    }
}
