package com.gitcoding.httputil

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class AppExecutors private constructor(
    private val diskIO: Executor = Executors.newSingleThreadExecutor(),
    private val networkIO: Executor = Executors.newFixedThreadPool(3),
    private val mainThread: Executor = MainThreadExecutor()
) {

    companion object {
        @Volatile
        private var instance: AppExecutors? = null

        fun getInstance(): AppExecutors {
            return instance ?: synchronized(this) {
                instance ?: AppExecutors().also { instance = it }
            }
        }
    }

    fun diskIO(): Executor {
        return diskIO
    }

    fun networkIO(): Executor {
        return networkIO
    }

    fun mainThread(): Executor {
        return mainThread
    }

    private class MainThreadExecutor : Executor {
        private val mainThreadHandler = Handler(Looper.getMainLooper())
        override fun execute(comm: Runnable?) {
            mainThreadHandler.post(comm!!)
        }

    }
}