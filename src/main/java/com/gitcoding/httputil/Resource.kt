package com.gitcoding.httputil

data class Resource<T>(
    val status: Status,
    val data: T? = null,
    val code: Int? = null,
    val msg: String? = null
) {

    companion object {
        fun <T> success(data: T?): Resource<T> {
            return Resource(Status.SUCCESS, data)
        }

        fun <T> empty(): Resource<T> {
            return Resource(Status.EMPTY)
        }

        fun <T> error(code: Int? = null, msg: String? = null): Resource<T> {
            return Resource(Status.ERROR, code = code, msg = msg)
        }

        fun <T> loading(): Resource<T> {
            return Resource(Status.LOADING)
        }
    }

    /**
     * 成功
     */
    fun isSuccess() = status == Status.SUCCESS

    /**
     * 数据为空
     */
    fun isEmpty() = status == Status.EMPTY

    /**
     * 加载中
     */
    fun isLoading() = status == Status.LOADING

    /**
     * 失败
     */
    fun isFailure() = status == Status.ERROR


}