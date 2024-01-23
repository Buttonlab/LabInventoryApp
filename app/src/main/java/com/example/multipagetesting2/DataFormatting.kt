package com.example.multipagetesting2

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Array
import java.lang.reflect.Type

// Used by the endpoints which dont otherwise return data like the kill and action endpoints
data class BasicResponse(
    val success: String?,
    val error: String?
)

data class FieldsResponse(
    val fields: MutableMap<String, ArrayList<String>>
): MutableMap<String, ArrayList<String>> by fields

data class SubstitutionResponse(
    val subs: MutableMap<String, Map<String, String>>?
)

data class ActionsResponse(
    val acts: MutableMap<String, Map<String, String>>?
)

data class CellsResponse(
    val cellCount: Int,
    val cells: List<String>
)

data class CellItem(
    val id: String?,
    val type: String?,
    val genotype: String?, // Only for Primary cells
    val distNum: String?, // Only for Primary cells
    val year: String?, // Only for Primary cells
    val owner: String?, // Only for Primary cells
    val cellType: String?, // Only for Immortal cells
    val geneMod: String?, // Only for Immortal cells
    val gene1: String?, // Only for Immortal cells
    val gene2: String?, // Only for Immortal cells
    val resistance: String?, // Only for Immortal cells
    val clone: String?, // Only for Immortal cells
    val passage: String?, // Single char base36 for Primary, a 2 byte hex for Immortal
    val surface: String?,
    val number: String?, // Single char base36 for both
    val unique: String?,
    val status: String?,
    val location: String?,
    val supportQuantity: String?,
    val media: String?,
    val mediaSupplements: String?,
    val antibiotics: String?,
    val wellCount: Int?,
    val creationDate: String?,
    val lastWash: String?,
    val lastFeed: String?,
    val feedingSchedule: String?,
    val washSchedule: String?,
    val treatmentStart: String?,
    val notes: String?
)

data class ActionRequest(
    val target: List<String>,
    val checksum: String,
    val actionName: String,
    val number: Int?,
    val fields: MutableMap<String, String>
)

data class QueuedApiRequest(
    val type: String,
    val param: String // The parameters to send, stored as json string
)


class SubstitutionResponseDeserializer : JsonDeserializer<SubstitutionResponse> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SubstitutionResponse {
        val subs = mutableMapOf<String, Map<String, String>>()
        val jsonObject = json.asJsonObject
        for (entry in jsonObject.entrySet()) {
            val innerMap = mutableMapOf<String, String>()
            val innerJsonObject = entry.value.asJsonObject
            for (innerEntry in innerJsonObject.entrySet()) {
                innerMap[innerEntry.key] = innerEntry.value.asString
            }
            subs[entry.key] = innerMap
        }
        return SubstitutionResponse(subs)
    }
}

class SubstitutionResponseSerializer : JsonSerializer<SubstitutionResponse> {
    override fun serialize(src: SubstitutionResponse?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val jsonObject = JsonObject()
        src?.subs?.forEach { (key, valueMap) ->
            val innerJsonObject = JsonObject()
            valueMap.forEach { (innerKey, innerValue) ->
                innerJsonObject.add(innerKey, JsonPrimitive(innerValue))
            }
            jsonObject.add(key, innerJsonObject)
        }
        return jsonObject
    }
}

class ActionResponseDeserializer : JsonDeserializer<ActionsResponse> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ActionsResponse {
        val acts = mutableMapOf<String, Map<String, String>>()
        val jsonObject = json.asJsonObject
        for (entry in jsonObject.entrySet()) {
            val innerMap = mutableMapOf<String, String>()
            val innerJsonObject = entry.value.asJsonObject
            for (innerEntry in innerJsonObject.entrySet()) {
                innerMap[innerEntry.key] = innerEntry.value.asString
            }
            acts[entry.key] = innerMap
        }
        return ActionsResponse(acts)
    }
}

class ActionResponseSerializer : JsonSerializer<ActionsResponse> {
    override fun serialize(src: ActionsResponse?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val jsonObject = JsonObject()
        src?.acts?.forEach { (key, valueMap) ->
            val innerJsonObject = JsonObject()
            valueMap.forEach { (innerKey, innerValue) ->
                innerJsonObject.add(innerKey, JsonPrimitive(innerValue))
            }
            jsonObject.add(key, innerJsonObject)
        }
        return jsonObject
    }
}