package com.github.httputil

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <R, API> ViewModel.request(
    api: API,
    start: (() -> Unit)? = null,
    empty: (() -> Unit)? = null,
    each: ((R) -> Unit)? = null,
    catch: (() -> Unit)? = null,
    completed: (() -> Unit)? = null,
    request: suspend API.() -> R?,
): Flow<R> {
    return flow {
        val response = request(api)
        if (response != null) {
            emit(response)
        }
    }.flowOn(Dispatchers.IO)
        .onStart {
            start?.invoke()
        }.onCompletion {
            completed?.invoke()
        }.onEmpty {
            empty?.invoke()
        }.onEach {
            each?.invoke(it)
        }.catch {
            catch?.invoke()
        }
}