package com.example.multipagetesting2
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    // These define the url and if needed the variables for the path or the body

    @GET("/api/ping")
    suspend fun pingAPI(): Response<BasicResponse>

    @GET("/api/general/substitutions")
    suspend fun getSubstitutions(): Response<SubstitutionResponse>

    @GET("/api/general/actions")
    suspend fun getActions(): Response<ActionsResponse>

    @GET("api/live/cells")
    suspend fun getLiveCells(): Response<CellsResponse>

    @GET("api/live/cells/{table}/fields")
    suspend fun getCellFields(@Path("table") table: String): Response<FieldsResponse>

    @GET("api/live/cells/id/{cellID}")
    suspend fun getCellById(@Path("cellID") cellID: String): Response<CellItem>

    @GET("api/live/kill/{cellID}/{checksum}")
    suspend fun killCellByID(@Path("cellID") cellID: String, @Path("checksum") checksum: String): Response<BasicResponse>

    @GET("api/live/{cellID}/oldest/{field}")
    suspend fun getOldestByField(@Path("cellID") cellID: String, @Path("field") field: String): Response<OldestResponse >

    @POST("/api/live/actions/apply")
    suspend fun applyActions(@Body requestBody: ActionRequest): Response<BasicResponse>
}