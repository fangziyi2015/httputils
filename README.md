###一、全局配置
#### 在Application的onCreate()方法中做全局配置

```
RetrofitManager
			// 添加自定义拦截器
            .addInterceptors(UrlInterceptor())
			// 设置是否开启缓存以及设置缓存路径
            .setCache(cachePath = cacheDir.path + "/cache")
			// 设置是否开启debug
            .setDebug(BuildConfig.DEBUG)
			// 如果全局只有一个baseUrl，可设置全局的baseUrl
            .setBaseUrl("https://devapi.qweather.com/v7/")
````

###二、创建独立的api service
####以下的任何一种方式都可以创建独立的ApiService,根据需要选择
```
    private val weatherApi by lazy {
        // 1、通过这种方式可以用全局的retrofit创建独立的api service
        RetrofitManager.getRetrofit().create(WeatherApi::class.java)
        // 2、通过这种方式可以用指定的retrofit创建独立的api service
        RetrofitManager.getRetrofit("https://devapi.qweather.com/v7/").create(WeatherApi::class.java)
        // 3、可以使用build构建的retrofit创建独立的api service,callAdapterFactory 可以不传，使用默认的callAdapterFactory
        RetrofitManager.build("https://devapi.qweather.com/v7/").create(WeatherApi::class.java)
        // 4、使用LiveDataCallAdapterFactory
        RetrofitManager.build("https://devapi.qweather.com/v7/",LiveDataCallAdapterFactory()).create(WeatherApi::class.java)
        // 5、使用FlowCallAdapterFactory
        RetrofitManager.build("https://devapi.qweather.com/v7/",FlowCallAdapterFactory()).create(WeatherApi::class.java)
    }
```

###三、内置的CallAdapterFactory
#### 1、 LiveDataCallAdapterFactory

#####示例1

```
@GET("weather/now")
    fun realWeather(
       @Query("location") location: String
    ): LiveData<RealWeather>
```
#####示例2

```
@GET("weather/now")
fun realWeatherApi(
     @Query("location") location: String
): LiveData<Resource<RealWeather>>
// 请求示例
fun realWeather(location: String) {
     weatherRepository.realWeatherApi(location).observeForever {
          realWeatherLiveData.value = it
      }
 }
```
#####示例3

```
@GET("weather/now")
    fun realWeatherApi(
        @Query("location") location: String
    ): LiveData<ApiResponse<RealWeather>>
```
#####示例4

```
@GET("weather/now")
    fun realWeatherApi(
        @Query("location") location: String
    ): LiveData<Response<RealWeather>>
```

#### 2、 FlowCallAdapterFactory

#####示例1

```
     @GET("weather/now")
        fun realWeather(
            @Query("location") location: String
        ): Flow<RealWeather>

       // 请求示例
        fun realWeatherFlow(location: String) {
        viewModelScope.launch {
            weatherRepository.realWeatherFlow(location).collectLatest {
                if (it.isSuccess()) {
                    realWeatherLiveData.value = Resource.success(it)
                }
            }
        }
    }
```
#####示例2

```
@GET("weather/now")
    fun realWeatherApi(
        @Query("location") location: String
    ): Flow<Resource<RealWeather>>
```
#####示例3

```
@GET("weather/now")
    fun realWeatherApi(
        @Query("location") location: String
    ): Flow<ApiResponse<RealWeather>>
```
#####示例3

```
@GET("weather/now")
    fun realWeatherApi(
        @Query("location") location: String
    ): Flow<Response<RealWeather>>
```
>以上几种方式的主要区别在于返回的数据的泛型类型上

###四、以Flow的方式请求数据
```
val realWeatherStateFlow = MutableStateFlow<RealWeather?>(null)
// api
private val weatherApi by lazy { RetrofitManager.getRetrofit().create(WeatherApi::class.java) }
// 发起请求
fun fetchRealWeatherInfo(location: String) {
    viewModelScope.launch {
        request(weatherApi) {
            fetchRealWeather(location)
        }.collectLatest {
            realWeatherStateFlow.value = it
        }
    }
}
// 在WeatherApi中,定义为suspend方法
interface WeatherApi{
    @GET("weather/now")
    suspend fun fetchRealWeather(
        @Query("location") location: String
    ): RealWeather?
}

```