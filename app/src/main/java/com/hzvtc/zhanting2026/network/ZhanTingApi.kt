package com.hzvtc.zhanting2026.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class HttpTextResult(
    val code: Int,
    val body: String
) {
    val isSuccessful: Boolean get() = code in 200..299
}

class ZhanTingApi {
    suspend fun getText(url: String, timeoutMs: Int = DefaultTimeoutMs): HttpTextResult =
        requestText(url, "GET", timeoutMs = timeoutMs)

    suspend fun postEmpty(url: String, timeoutMs: Int = DefaultTimeoutMs): HttpTextResult =
        requestText(url, "POST", timeoutMs = timeoutMs)

    suspend fun postForm(
        url: String,
        fields: Map<String, String>,
        timeoutMs: Int = DefaultTimeoutMs
    ): HttpTextResult = withContext(Dispatchers.IO) {
        val body = fields
            .map { (key, value) -> "${encode(key)}=${encode(value)}" }
            .joinToString("&")
            .toByteArray(StandardCharsets.UTF_8)

        val connection = open(url, "POST", timeoutMs)
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(body) }
        readTextResult(connection)
    }

    suspend fun uploadFile(
        url: String,
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String = "image/jpeg",
        timeoutMs: Int = UploadTimeoutMs
    ): HttpTextResult = withContext(Dispatchers.IO) {
        val boundary = "----ZhanTing2026${System.currentTimeMillis()}"
        val connection = open(url, "POST", timeoutMs)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true

        connection.outputStream.use { output ->
            fun write(value: String) = output.write(value.toByteArray(StandardCharsets.UTF_8))

            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
            write("Content-Type: $mimeType\r\n\r\n")
            output.write(bytes)
            write("\r\n--$boundary--\r\n")
        }

        readTextResult(connection)
    }

    suspend fun headOrGet(url: String, timeoutMs: Int = DefaultTimeoutMs): HttpTextResult = withContext(Dispatchers.IO) {
        val head = requestText(url, "HEAD", timeoutMs)
        if (head.code == HttpURLConnection.HTTP_BAD_METHOD) {
            requestText(url, "GET", timeoutMs)
        } else {
            head
        }
    }

    private suspend fun requestText(
        url: String,
        method: String,
        timeoutMs: Int = DefaultTimeoutMs
    ): HttpTextResult = withContext(Dispatchers.IO) {
        val connection = open(url, method, timeoutMs)
        readTextResult(connection)
    }

    private fun open(url: String, method: String, timeoutMs: Int): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Accept", "*/*")
        }
    }

    private fun readTextResult(connection: HttpURLConnection): HttpTextResult {
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.bufferedReader(Charsets.UTF_8).readText() }.orEmpty()
            HttpTextResult(code, body.trim())
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val DefaultTimeoutMs = 8_000
        private const val UploadTimeoutMs = 60_000
    }
}
