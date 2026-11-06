package com.example.roombooking.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException

/** An error the UI can show directly. [code] is the server's error code, e.g. "BOOKING_CONFLICT". */
class AppException(
    message: String,
    val code: String? = null,
    val httpStatus: Int? = null,
    val details: Map<String, Any>? = null,
) : Exception(message)

/**
 * Runs a Retrofit call and turns every failure into an [AppException] with a Thai message:
 *  - HTTP 4xx/5xx -> the server's {"error": {"code", "message"}} body
 *  - no network   -> "เชื่อมต่อเซิร์ฟเวอร์ไม่ได้"
 * CancellationException is not caught, so coroutine cancellation still works.
 */
suspend fun <T> apiCall(gson: Gson, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    Result.failure(parseHttpError(gson, e.code(), e.response()?.errorBody()?.string()))
} catch (e: IOException) {
    Result.failure(AppException("เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ต", "NETWORK"))
} catch (e: JsonParseException) {
    Result.failure(AppException("ข้อมูลจากเซิร์ฟเวอร์ไม่ถูกต้อง", "PARSE_ERROR"))
}

/** For endpoints that return 204 No Content (declared as Response<Unit>). */
fun retrofit2.Response<Unit>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}

fun parseHttpError(gson: Gson, status: Int, body: String?): AppException {
    val detail = try {
        body?.let { gson.fromJson(it, ErrorEnvelope::class.java)?.error }
    } catch (e: JsonParseException) {
        null
    }
    val fallback = when (status) {
        401 -> "กรุณาเข้าสู่ระบบใหม่"
        403 -> "คุณไม่มีสิทธิ์ทำรายการนี้"
        404 -> "ไม่พบข้อมูล"
        409 -> "ข้อมูลขัดแย้ง กรุณาลองใหม่"
        in 500..599 -> "เซิร์ฟเวอร์ขัดข้อง กรุณาลองใหม่ภายหลัง"
        else -> "เกิดข้อผิดพลาด ($status)"
    }
    return AppException(detail?.message ?: fallback, detail?.code, status, detail?.details)
}
