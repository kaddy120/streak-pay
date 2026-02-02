package com.workpointstracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object ImageUtils {

    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val filename = "wish_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            return filename
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    fun getImageFile(context: Context, filename: String): File {
        return File(context.filesDir, filename)
    }

    fun deleteImage(context: Context, filename: String): Boolean {
        val file = File(context.filesDir, filename)
        return if (file.exists()) file.delete() else false
    }
}
