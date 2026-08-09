package com.ramzes.visavinet.util

import android.content.Context
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

object FileUtils {
    /**
     * Преобразовать Uri в MultipartBody.Part с потоковой передачей данных
     */
    fun uriToMultipartBodyPart(context: Context, uri: Uri, partName: String = "files[]"): MultipartBody.Part? {
        return try {
            val contentResolver = context.contentResolver
            val type = contentResolver.getType(uri) ?: "application/octet-stream"
            val mediaType = type.toMediaTypeOrNull()
            val filename = getFileName(context, uri) ?: "upload_file"

            val requestBody = object : RequestBody() {
                override fun contentType(): MediaType? = mediaType

                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        sink.writeAll(inputStream.source())
                    }
                }
            }

            MultipartBody.Part.createFormData(partName, filename, requestBody)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = c.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1 && result != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    fun stringToTextPart(value: String): MultipartBody.Part {
        return MultipartBody.Part.createFormData("text", value)
    }

    fun stringToTitlePart(value: String): MultipartBody.Part {
        return MultipartBody.Part.createFormData("title", value)
    }
}
