package com.example.androidfetch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.TreeMap

class JavascriptBridge {
    private val webView: WebView
    private val context: Context

    constructor(webView: WebView, context: Context) {
        this.webView = webView
        this.context = context
    }

    @JavascriptInterface
    fun showToast(toast: String?) {
        Toast.makeText(this.context, toast, Toast.LENGTH_SHORT).show()
    }


    @JavascriptInterface
    fun fetch(url: String, method: String, headers: String, data: String, id: String) {
        val dataJsonArray: JSONArray = JSONArray(data)
        val headersObj = JSONObject(headers)
        doFetch(url, method, headersObj, dataJsonArray, 0, id)
    }

    fun doFetch(
        url: String,
        method: String,
        headers: JSONObject,
        data: JSONArray,
        redirectCount: Int,
        id: String,
    ) {

        val mainHandler = Handler(Looper.getMainLooper())
        var connection: HttpURLConnection
        val networkTask = Runnable {
            try {
                val url = URL(url)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = method
                setRequestHeaders(connection, headers)
                if (data.length() > 0) {
                    setRequestBody(connection, data)
                }
                connection.connect()
                val status = connection.responseCode
                val statusText = connection.responseMessage
                val headerFields = connection.headerFields
                if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP) {
                    connection.disconnect()
                    val redirectUrl = lowercaseMap(headerFields)["location"]?.get(0)
                    if (redirectUrl != null) {
                        doFetch(redirectUrl, method, headers, data, redirectCount + 1, id)
                    }
                    return@Runnable
                }
                if (status == HttpURLConnection.HTTP_OK) {
                    mainHandler.post(Runnable {
                        webView.evaluateJavascript(
                            "requestResolve($id, $status, '$statusText', ${
                                headersToJSONObject(
                                    headerFields
                                )
                            })"
                        ) {}
                    })
                }
            } catch (e: IOException) {
                e.printStackTrace()
                webView.evaluateJavascript("requestFail($id)") {}
                return@Runnable
            }

            var nRead: Int
            val data = ByteArray(32)

            try {
                while ((connection.inputStream.read(data, 0, data.size)
                        .also { nRead = it }) != -1
                ) {
                    val slicedArray = ByteArray(nRead)
                    System.arraycopy(data, 0, slicedArray, 0, nRead)
                    mainHandler.post(Runnable {
                        webView.evaluateJavascript(
                            "streamPush($id, ${
                                byteArrayToJSONArray(
                                    slicedArray
                                )
                            })"
                        ) {}
                    })
                }

                Log.i("Test", nRead.toString())
                mainHandler.post(Runnable {
                    webView.evaluateJavascript("streamSucceed($id)") {}
                })
            } catch (e: IOException) {

                mainHandler.post(Runnable {
                    webView.evaluateJavascript("streamError($id)") {}
                })

            }
        }
        Thread(networkTask).start()
    }
}

fun stringListToJSONArray(stringList: List<String>): JSONArray {
    val jsonArray = JSONArray()
    for (item in stringList) {
        jsonArray.put(item) // 添加字符串到JSONArray
    }
    return jsonArray
}

fun lowercaseMap(headers: Map<String, List<String>>): Map<String, List<String>> {
    // 使用TreeMap来保证键的排序，同时保持不区分大小写的特性
    val caseInsensitiveHeaders: MutableMap<String, List<String>> =
        TreeMap(java.lang.String.CASE_INSENSITIVE_ORDER)
    for ((key, value) in headers) {
        var keyLowerCase = key
        if (key != null) {
            keyLowerCase = key.lowercase()
            caseInsensitiveHeaders[keyLowerCase] = value
        }
    }
    return caseInsensitiveHeaders
}

fun headersToJSONObject(headers: Map<String, List<String>>): JSONObject {
    val headersJson = JSONObject()
    for ((key, value) in headers) {
        if (key != null) {
            headersJson.put(key, stringListToJSONArray(value))
        }
    }
    return headersJson
}

fun byteArrayToJSONArray(byteArray: ByteArray): JSONArray {
    val jsonArray = JSONArray()
    for (b in byteArray) {
        // 将每个字节转换为整数，并添加到JSON数组中
        jsonArray.put(b.toInt() and 0xFF) // 使用位运算确保在0-255范围内
    }
    return jsonArray
}


fun setRequestHeaders(conn: HttpURLConnection, headers: JSONObject) {
    val keys: Iterator<String> = headers.keys()

    while (keys.hasNext()) {
        val key = keys.next() // 获取下一个键
        val value: String = headers.optString(key) // 通过键获取值
        conn.setRequestProperty(key, value)
    }
}

fun setRequestBody(conn: HttpURLConnection, data: JSONArray) {
    Log.i("datalengt", data.length().toString())
    val buffer = ByteBuffer.allocate(data.length())
    for (i in 0 until data.length()) {
        val num = data.getInt(i)
        buffer.put(num.toByte())
    }
    val os = conn.outputStream
    os.write(buffer.array())
    os.flush()
}
