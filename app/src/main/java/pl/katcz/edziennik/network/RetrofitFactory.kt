package pl.katcz.edziennik.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject

class RetrofitFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    fun create(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .build()
}
