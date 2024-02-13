package com.example.multipagetesting2

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException

// TODO: Need to implement a system to queue API call while network is not accessible

object DataRepository {

    // ==========================================================
    //                          SETUP
    // ==========================================================

    // Function to get the ip from a fragment  or activity
    private var apiIP = "192.168.1.217:8080" // Default setting for now, will not always work when API computer changes
    fun setApiIP(ip: String) {
        // Assuming that the IP has already been checked for validity and works
        apiIP = ip
        initRetrofit()
    }

    // Logging setup
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // Setup for the JSON converting
    private val gson = GsonBuilder()
        .registerTypeAdapter(SubstitutionResponse::class.java, SubstitutionResponseDeserializer())
        .registerTypeAdapter(ActionsResponse::class.java, ActionResponseDeserializer())
        .create()

    private lateinit var apiService: ApiService
    private lateinit var retrofit: Retrofit

    private fun initRetrofit() {

        // Retrofit setup
        retrofit = Retrofit.Builder()
            .baseUrl("http://$apiIP/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient) // Attaching the logger
            .build()

        // Creating the api service
        apiService = retrofit.create(ApiService::class.java)
    }

    // Holds the substitutions the API sent over for later access by the fragments
    var substitutions: SubstitutionResponse? = null

    // Holds the actions the API sent over for later access by the fragments
    var actions: ActionsResponse? = null


    init {
        initRetrofit()
    }


    // ==========================================================
    //             Defining the endpoints to access
    // ==========================================================

    suspend fun pingAPI(): Boolean {
        try {
            val response = apiService.pingAPI()
            if (response.isSuccessful) {
                return true
            } else {
                try {
                    Log.d("DataRepository", "Ping failed with body: ${response.body()}")
                } catch (e: Exception) {
                    Log.d("DataRepository", "Ping failed with body: ${response.errorBody()}")
                }
                return false
            }
        } catch (e: ConnectException) {
            Log.e("DataRepository", "ConnectException, could not reach network")
            return false
        } catch (e: Exception) {
            Log.e("DataRepository", "General Exception in contacting API")
            return false
        }
    }

    suspend fun getSubstitutions(): SubstitutionResponse? {
        if (substitutions == null) {
            val response = apiService.getSubstitutions()
            if (response.isSuccessful) {
                substitutions = response.body()
            } else {
                substitutions = null
            }
            Log.d("DataRepository", "Substitutions fetched: $substitutions")
        }
        return substitutions
    }

    suspend fun getActions(): ActionsResponse? {
        if (actions == null) {
            val response = apiService.getActions()
            if (response.isSuccessful) {
                actions = response.body()
            } else {
                actions = null
            }
            Log.d("DataRepository", "Actions fetched: $actions")
        }
        return actions
    }

    suspend fun getLiveCells(): Response<CellsResponse> {
        return apiService.getLiveCells()
    }

    suspend fun getCellFields(table: String): Response<FieldsResponse> {
        return apiService.getCellFields(table)
    }

    suspend fun getCellByID(cellID: String): Response<CellItem> {
        return apiService.getCellById(cellID)
    }

    suspend fun getOldestByField(cellID: String, field: String): Response<OldestResponse> {
        return apiService.getOldestByField(cellID, field)
    }

    suspend fun killCellByID(cellID: String, checksum: String): Response<BasicResponse> {
        return apiService.killCellByID(cellID, checksum)
    }

    suspend fun applyActions(request: ActionRequest): Response<BasicResponse> {
        return apiService.applyActions(request)
    }
}