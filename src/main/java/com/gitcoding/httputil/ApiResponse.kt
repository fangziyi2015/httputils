package com.gitcoding.httputil

import retrofit2.HttpException
import retrofit2.Response

sealed class ApiResponse<T> {
    companion object {
        fun <T> create(errorCode: Int? = null, throwable: Throwable): ApiErrorResponse<T> {
            return ApiErrorResponse(errorCode, throwable.message)
        }

        fun <T> create(response: Response<T>): ApiResponse<T> {
            return if (response.isSuccessful) {
                val body = response.body()
                if (body == null || response.code() == 204 || (body is Collection<*> && body.isEmpty())) {
                    ApiEmptyResponse()
                } else {
                    ApiSuccessResponse(response.body())
                }
            } else {
                val httpException = HttpException(response)
                var errorMsg = httpException.message()
                errorMsg = if (errorMsg.isNullOrEmpty()) {
                    response.message() ?: "unknown error"
                } else {
                    errorMsg
                }
                ApiErrorResponse(httpException.code(), errorMsg)
            }

        }
    }

    class ApiEmptyResponse<T> : ApiResponse<T>()

    data class ApiSuccessResponse<T>(val data: T?) : ApiResponse<T>()

    data class ApiErrorResponse<T>(val code: Int? = null, val msg: String? = null) :
        ApiResponse<T>()
}

