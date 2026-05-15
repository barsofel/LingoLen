package ofelya.barseghyan.lingolens

import retrofit2.http.Body
import retrofit2.http.POST

interface TranslateApi {
    @POST("translate")
    suspend fun translate(
        @Body request: TranslateRequest
    ): TranslateResponse
}