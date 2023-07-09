package com.github.httputil.factory

import androidx.lifecycle.LiveData
import com.github.httputil.*
import retrofit2.*
import retrofit2.CallAdapter.Factory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicBoolean

class LiveDataCallAdapterFactory : Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {

        if (getRawType(returnType) != LiveData::class.java) {
            return null
        }

        check(returnType is ParameterizedType) { "LiveData return type must be parameterized as LiveData<Foo> or LiveData<out Foo>" }
        //先解释一下getParameterUpperBound
        //官方例子
        //For example, index 1 of {@code Map<String, ? extends Runnable>} returns {@code Runnable}.
        //获取的是Map<String,? extends Runnable>参数列表中index序列号的参数类型,即0为String,1为Runnable
        //这里的0就是LiveData<?>中?的序列号,因为只有一个参数
        //其实这个就是我们请求返回的实体
        val responseType = getParameterUpperBound(0, returnType)
        return when (getRawType(responseType)) {
            Resource::class.java -> {
                check(responseType is ParameterizedType) { "Resource must be parameterized as Resource<Foo> or Resource<out Foo>" }
                ResourceCallAdapter<Any>(getParameterUpperBound(0, responseType))
            }
            ApiResponse::class.java -> {
                check(responseType is ParameterizedType) { "ApiResponse must be parameterized as ApiResponse<Foo> or ApiResponse<out Foo>" }
                ApiResponseCallAdapter<Any>(getParameterUpperBound(0, responseType))
            }
            Response::class.java -> {
                check(responseType is ParameterizedType) { "Response must be parameterized as Response<Foo> or Response<out Foo>" }
                ResponseCallAdapter<Any>(getParameterUpperBound(0, responseType))
            }
            else -> {
                BodyCallAdapter<Any>(responseType)
            }
        }
    }

    private class BodyCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, LiveData<R>> {

        override fun responseType() = responseType

        override fun adapt(call: Call<R>): LiveData<R> {
            return object : LiveData<R>() {
                //这个作用是业务在多线程中,业务处理的线程安全问题,确保单一线程作业
                private var started = AtomicBoolean(false)
                override fun onActive() {
                    super.onActive()
                    if (started.compareAndSet(false, true)) {
                        call.enqueue(object : Callback<R> {
                            override fun onResponse(call: Call<R>, response: Response<R>) {
                                if (response.isSuccessful) {
                                    postValue(response.body())
                                } else {
                                    postValue(null)
                                }
                            }

                            override fun onFailure(call: Call<R>, throwable: Throwable) {
                                postValue(null)
                            }
                        })
                    }
                }
            }
        }
    }

    private class ResourceCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, LiveData<Resource<R>>> {

        override fun responseType() = responseType

        override fun adapt(call: Call<R>): LiveData<Resource<R>> {
            return object : LiveData<Resource<R>>() {
                //这个作用是业务在多线程中,业务处理的线程安全问题,确保单一线程作业
                private var started = AtomicBoolean(false)
                override fun onActive() {
                    super.onActive()
                    if (started.compareAndSet(false, true)) {
                        call.enqueue(object : Callback<R> {
                            override fun onResponse(call: Call<R>, response: Response<R>) {
                                if (response.isSuccessful) {
                                    val body = response.body()
                                    if (body == null || response.code() == 204 || (body is Collection<*> && body.isEmpty())) {
                                        postValue(Resource.empty())
                                    } else {
                                        postValue(Resource.success(body))
                                    }
                                } else {
                                    val httpException = HttpException(response)
                                    var errorMsg = httpException.message()
                                    errorMsg = if (errorMsg.isNullOrEmpty()) {
                                        response.message() ?: "unknown error"
                                    } else {
                                        errorMsg
                                    }
                                    postValue(Resource.error(httpException.code(), errorMsg))
                                }
                            }

                            override fun onFailure(call: Call<R>, throwable: Throwable) {
                                postValue(Resource.error(msg = throwable.message))
                            }
                        })
                    }
                }
            }
        }

    }

    private class ApiResponseCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, LiveData<ApiResponse<R>>> {

        override fun responseType() = responseType

        override fun adapt(call: Call<R>): LiveData<ApiResponse<R>> {
            return object : LiveData<ApiResponse<R>>() {
                //这个作用是业务在多线程中,业务处理的线程安全问题,确保单一线程作业
                private var started = AtomicBoolean(false)
                override fun onActive() {
                    super.onActive()
                    if (started.compareAndSet(false, true)) {
                        call.enqueue(object : Callback<R> {
                            override fun onResponse(call: Call<R>, response: Response<R>) {
                                postValue(ApiResponse.create(response))
                            }

                            override fun onFailure(call: Call<R>, throwable: Throwable) {
                                postValue(ApiResponse.create(throwable = throwable))
                            }
                        })
                    }
                }
            }
        }

    }

    private class ResponseCallAdapter<R>(private val responseType: Type) :
        CallAdapter<R, LiveData<Response<R>>> {

        override fun responseType() = responseType

        override fun adapt(call: Call<R>): LiveData<Response<R>> {
            return object : LiveData<Response<R>>() {
                //这个作用是业务在多线程中,业务处理的线程安全问题,确保单一线程作业
                private var started = AtomicBoolean(false)
                override fun onActive() {
                    super.onActive()
                    if (started.compareAndSet(false, true)) {
                        call.enqueue(object : Callback<R> {
                            override fun onResponse(call: Call<R>, response: Response<R>) {
                                postValue(response)
                            }

                            override fun onFailure(call: Call<R>, throwable: Throwable) {
                                postValue(null)
                            }
                        })
                    }
                }
            }
        }

    }
}



