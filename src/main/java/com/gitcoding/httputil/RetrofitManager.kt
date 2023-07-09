package com.gitcoding.httputil

import android.os.Environment
import com.gitcoding.httputil.factory.LiveDataCallAdapterFactory
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.CallAdapter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetrofitManager {

    private const val DEFAULT_TIMEOUT = 5L

    private val interceptors = mutableListOf<Interceptor>()
    private val retrofitCache = mutableMapOf<String, Retrofit>()

    private var cachePath = Environment.getDataDirectory().path.plus("/okhttp/cache")
    private var baseUrl = ""
    private var timeOut = DEFAULT_TIMEOUT
    private var isCache = false
    private var isDebug = true

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().apply {
            if (isCache) {
                cache(Cache(File(cachePath), 1024 * 1024 * 10L))
            }
            // 应用拦截器
            interceptors.forEach {
                addInterceptor(it)
            }
            // Logger
            addInterceptor(HttpLoggingInterceptor().apply {
                level =
                    if (isDebug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
            })
            connectTimeout(timeOut, TimeUnit.SECONDS)
            readTimeout(timeOut, TimeUnit.SECONDS)
            writeTimeout(timeOut, TimeUnit.SECONDS)
            retryOnConnectionFailure(true)
        }.build()
    }

    /**
     * 添加自定义拦截器
     */
    fun addInterceptors(interceptor: Interceptor): RetrofitManager {
        if (!interceptors.contains(interceptor)) {
            interceptors.add(interceptor)
        }
        return this
    }

    /**
     * 是否开启缓存，可设置缓存路径
     */
    fun setCache(isCache: Boolean = true, cachePath: String = ""): RetrofitManager {
        this.isCache = isCache
        if (cachePath.isNotEmpty()) this.cachePath = cachePath
        return this
    }

    /**
     * 设置超时时间
     */
    fun setTimeOut(timeOut: Long = DEFAULT_TIMEOUT): RetrofitManager {
        if (timeOut != DEFAULT_TIMEOUT) {
            this.timeOut = timeOut
        }
        return this
    }

    /**
     * 是否开启debug
     */
    fun setDebug(isDebug: Boolean = true): RetrofitManager {
        this.isDebug = isDebug
        return this
    }

    /**
     * 可设置全局baseUrl
     */
    fun setBaseUrl(baseUrl: String): RetrofitManager {
        this.baseUrl = baseUrl
        return this
    }

    /**
     * 可获取全局retrofit
     */
    fun getRetrofit(baseUrl: String = this.baseUrl): Retrofit {
        return build(baseUrl)
    }

    /**
     * 可根据不同的baseUrl构建不同的retrofit
     */
    fun build(
        baseUrl: String = this.baseUrl,
        callAdapterFactory: CallAdapter.Factory = LiveDataCallAdapterFactory()
    ): Retrofit {
        val retrofit = retrofitCache[baseUrl]
        return retrofit ?: synchronized(this) {
            retrofit ?: Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                // 支持suspend方法
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                // 支持LiveData
                .addCallAdapterFactory(callAdapterFactory)
                .build().also {
                    retrofitCache[baseUrl] = it
                }
        }
    }
}