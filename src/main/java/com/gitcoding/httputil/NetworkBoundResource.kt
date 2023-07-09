package com.gitcoding.httputil

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

abstract class NetworkBoundResource<ResultType, RequestType>
@MainThread constructor(private val appExecutors: AppExecutors) {

    private val result = MediatorLiveData<Resource<ResultType>>()

    init {
        // 加载中状态
        result.value = Resource.loading()
        // 在IO线程执行查询的操作
        appExecutors.diskIO().execute {
            // 先从数据库中加载数据
            val dbSource = loadFromDb()
            // 切换到主线程
            appExecutors.mainThread().execute {
                result.addSource(dbSource) { data ->
                    // 移除原LiveData
                    result.removeSource(dbSource)
                    // 如果数据不是空，则先显示本地数据
                    if (!shouldFetch(data)) {
                        result.addSource(dbSource) { newData ->
                            result.removeSource(dbSource)
                            setValue(Resource.success(newData))
                        }
                    }
                    // 再从网络拉取数据
                    fetchFromNetwork(dbSource)
                }
            }
        }
    }

    private fun fetchFromNetwork(dbSource: LiveData<ResultType?>) {
        // 从网络端拉取数据
        val apiResponse = fetchNetwork()
        result.addSource(apiResponse) { response ->
            result.removeSource(apiResponse)
            result.removeSource(dbSource)
            if (response != null) {
                // 切换到IO线程，需要保存数据到本地
                appExecutors.diskIO().execute {
                    // 保存回调数据
                    saveCallResult(response)
                    // 从数据库拉取数据
                    val db = loadFromDb()
                    // 切换到主线程
                    appExecutors.mainThread().execute {
                        result.addSource(db) { result ->
                            setValue(Resource.success(result))
                        }
                    }
                }
            }
        }
    }

    private fun setValue(data: Resource<ResultType>) {
        if (result.value != data) {
            result.postValue(data)
        }
    }

    fun asLiveData() = result as LiveData<Resource<ResultType>>

    @WorkerThread
    abstract fun loadFromDb(): LiveData<ResultType?>

    @WorkerThread
    abstract fun saveCallResult(data: RequestType)

    @WorkerThread
    abstract fun fetchNetwork(): LiveData<RequestType>

    @MainThread
    abstract fun shouldFetch(data: ResultType?): Boolean
}