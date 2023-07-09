package com.github.httputil.factory

import com.github.httputil.ApiResponse
import com.github.httputil.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.*
import retrofit2.CallAdapter.Factory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FlowCallAdapterFactory : Factory() {
    companion object {
        @JvmStatic
        @JvmName("create")
        operator fun invoke() = FlowCallAdapterFactory()
    }

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {

        if (getRawType(returnType) != Flow::class.java) {
            return null
        }

        check(returnType is ParameterizedType) { "Flow return type must be parameterized as Flow<Foo> or Flow<out Foo>" }

        val responseType = getParameterUpperBound(0, returnType)
        return when (getRawType(responseType)) {
            Resource::class.java -> {
                check(responseType is ParameterizedType) { "Resource must be parameterized as Resource<Foo> or Resource<out Foo>" }
                ResourceCallAdapter<Any>(
                    getParameterUpperBound(
                        0,
                        responseType
                    )
                )
            }
            ApiResponse::class.java -> {
                check(responseType is ParameterizedType) { "ApiResponse must be parameterized as ApiResponse<Foo> or ApiResponse<out Foo>" }
                ApiResponseCallAdapter<Any>(
                    getParameterUpperBound(
                        0,
                        responseType
                    )
                )
            }
            Response::class.java -> {
                check(responseType is ParameterizedType) { "Response must be parameterized as Response<Foo> or Response<out Foo>" }
                ResponseCallAdapter<Any>(
                    getParameterUpperBound(
                        0,
                        responseType
                    )
                )
            }
            else -> {
                BodyCallAdapter<Any>(responseType)
            }
        }
    }

    private class ResponseCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, Flow<Response<R>>> {

        override fun responseType() = responseType

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun adapt(call: Call<R>): Flow<Response<R>> {
            return flow {
                suspendCancellableCoroutine { continuation ->
                    call.enqueue(object : Callback<R> {
                        override fun onResponse(call: Call<R>, response: Response<R>) {
                            continuation.resume(response)
                        }

                        override fun onFailure(call: Call<R>, t: Throwable) {
                            continuation.resumeWithException(t)
                        }

                    })

                    continuation.invokeOnCancellation { call.cancel() }
                }.let {
                    emit(it)
                }
            }
        }
    }

    private class ApiResponseCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, Flow<ApiResponse<R>>> {

        override fun responseType() = responseType

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun adapt(call: Call<R>): Flow<ApiResponse<R>> {
            return flow {
                suspendCancellableCoroutine<ApiResponse<R>> { continuation ->
                    call.enqueue(object : Callback<R> {
                        override fun onResponse(call: Call<R>, response: Response<R>) {
                            continuation.resume(ApiResponse.create(response))
                        }

                        override fun onFailure(call: Call<R>, t: Throwable) {
                            continuation.resume(ApiResponse.create(throwable = t))
                        }

                    })

                    continuation.invokeOnCancellation { call.cancel() }
                }.let {
                    emit(it)
                }
            }
        }
    }

    private class ResourceCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, Flow<Resource<R>>> {

        override fun responseType() = responseType

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun adapt(call: Call<R>): Flow<Resource<R>> {
            return flow {
                suspendCancellableCoroutine<Resource<R>> { continuation ->
                    call.enqueue(object : Callback<R> {
                        override fun onResponse(call: Call<R>, response: Response<R>) {
                            if (response.isSuccessful) {
                                val body = response.body()
                                if (body == null || response.code() == 204 || (body is Collection<*> && body.isEmpty())) {
                                    continuation.resume(Resource.empty())
                                } else {
                                    continuation.resume(Resource.success(body))
                                }
                            } else {
                                val httpException = HttpException(response)
                                var errorMsg = httpException.message()
                                errorMsg = if (errorMsg.isNullOrEmpty()) {
                                    response.message() ?: "unknown error"
                                } else {
                                    errorMsg
                                }
                                continuation.resume(Resource.error(httpException.code(), errorMsg))
                            }
                        }

                        override fun onFailure(call: Call<R>, t: Throwable) {
                            continuation.resume(Resource.error(msg = t.message))
                        }

                    })

                    continuation.invokeOnCancellation { call.cancel() }
                }.let {
                    emit(it)
                }
            }
        }
    }

    private class BodyCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, Flow<R>> {

        override fun responseType() = responseType

        override fun adapt(call: Call<R>): Flow<R> {
            return flow {
                suspendCancellableCoroutine<R> { continuation ->
                    call.enqueue(object : Callback<R> {
                        override fun onResponse(call: Call<R>, response: Response<R>) {
                            if (response.isSuccessful) {
                                try {
                                    continuation.resume(response.body()!!)
                                } catch (e: Exception) {
                                    continuation.resumeWithException(e)
                                }
                            } else {
                                continuation.resumeWithException(HttpException(response))
                            }
                        }

                        override fun onFailure(call: Call<R>, t: Throwable) {
                            continuation.resumeWithException(t)
                        }

                    })

                    continuation.invokeOnCancellation { call.cancel() }
                }.let {
                    emit(it)
                }
            }
        }
    }
}

