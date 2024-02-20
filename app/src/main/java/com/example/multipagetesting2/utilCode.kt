package com.example.multipagetesting2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.Locale
import java.util.zip.CRC32

private class LocalDateTimeSerializer : JsonSerializer<LocalDateTime> {
    override fun serialize(src: LocalDateTime, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

private class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDateTime {
        return LocalDateTime.parse(json.asString)
    }
}

private fun createGsonInstance(): Gson {
    return GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeSerializer())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()
}


// Function to get the queue from SharedPreferences
fun getQueueFromPreferences(sharedPreferences: SharedPreferences): MutableList<QueuedApiRequest> {
    val queueJson = sharedPreferences.getString("queue", "[]")
    return createGsonInstance().fromJson(queueJson, object : TypeToken<MutableList<QueuedApiRequest>>() {}.type)
}


// Function to save API request to the queue
fun saveRequestToQueue(context: Context, request: QueuedApiRequest) {
    val sharedPreferences = context.getSharedPreferences("${context.packageName}.ApiQueue", Context.MODE_PRIVATE)
    val queue = getQueueFromPreferences(sharedPreferences)
    queue.add(request)
    with(sharedPreferences.edit()) {
        putString("queue", createGsonInstance().toJson(queue))
        apply()
    }
}

// Function to replace the queue in SharedPreferences entirely
fun replaceRequestQueue(context: Context, queue: MutableList<QueuedApiRequest>) {
    val sharedPreferences = context.getSharedPreferences("${context.packageName}.ApiQueue", Context.MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        putString("queue", createGsonInstance().toJson(queue))
        apply()
    }
}


// Function to empty the queue
 fun clearRequestQueue(context: Context) {
    val sharedPreferences = context.getSharedPreferences("${context.packageName}.ApiQueue", Context.MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        remove("queue")
        apply()
    }
 }

// Function to check if the request queue is empty
fun isQueueEmpty(context: Context): Boolean {
    val sharedPreferences = context.getSharedPreferences("${context.packageName}.ApiQueue", Context.MODE_PRIVATE)
    val queue = getQueueFromPreferences(sharedPreferences)
    return queue.isEmpty()
}

// Function to convert the given hex value to ascii (and leave the unique as hex)
fun hexToTagAscii(hex: String?): String {
    Log.d("KillViewModel", "The given hex: $hex")
    if (hex.isNullOrEmpty()) {
        return ""
    }

    if (hex.startsWith("http")) {
        return hex.split("/").last()
    }
    try {
        var hexIn = hex
        if (hex.startsWith("68747470")) {
            val tempConversion = hexToAscii(hex)
            val lastSlashIndex = tempConversion.lastIndexOf('/') + 1
            if (lastSlashIndex != -1) {
                hexIn = hex.substring(lastSlashIndex * 2)
            }
        }

        val type = hexToAscii(hexIn.substring(0,2))
        if (type == "1" || type == "3") {
            val toConvert = hexIn.dropLast(4)
            val unique = hexIn.takeLast(4)
            return hexToAscii(toConvert) +
                    unique
        } else if (type == "2" || type == "4") {
            val toConvert1 = hexIn.substring(0,12)
            val clone = hexIn.substring(12, 14)
            val passage = hexIn.substring(14, 16)
            val toConvert2 = hexIn.substring(16, 20)
            val unique = hexIn.substring(20)
            return hexToAscii(toConvert1) +
                    clone +
                    passage +
                    hexToAscii(toConvert2) +
                    unique
        } else {
            return ""
        }
    } catch (e: Exception) {
        e.printStackTrace()
        val hexPattern = "^[0-9a-fA-F]+$".toRegex()
        if (!hexPattern.matches(hex)) { // This will just return the existing given value if it contains non-hex characters
            return hex
        }
        return ""
    }

}

// Function to convert the given ascii value to hex assuming its in tag format
fun tagAsciiToHex(asciiStr: String): String {
    Log.d("KillViewModel", "$asciiStr to hex")
    try {
        val type = asciiStr.first().toString()
        if (type == "1" || type == "3") {
            val toConvert = asciiStr.dropLast(4)
            val unique = asciiStr.takeLast(4)
            return asciiToHex(toConvert) +
                    unique
        } else if (type == "2" || type == "4") {
            val toConvert1 = asciiStr.substring(0, 6)
            val clone = asciiStr.substring(6, 8)
            val passage = asciiStr.substring(8, 10)
            val toConvert2 = asciiStr.substring(10, 12)
            val unique = asciiStr.substring(12)
            return asciiToHex(toConvert1) +
                    clone +
                    passage +
                    asciiToHex(toConvert2) +
                    unique
        } else {
            return ""
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return ""
    }

}

// Function to convert a given ascii string to hex
fun asciiToHex(asciiStr: String): String {
    try {
        return buildString {
            for (char in asciiStr) {
                append(char.code.toString(16))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return ""
    }
}


// Function to convert the given hex value to ascii
fun hexToAscii(hex: String): String {
    try {
        return buildString {
            for (i in hex.indices step 2) {
                val str = hex.substring(i, i + 2)
                val value = str.toInt(16)
                if (value in 1..127) {
                    append(str.toInt(16).toChar())
                } else {
                    append('?')
                }

            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return ""
    }

}

// Function to convert the tag hex to the crc32 password
fun hexToCrc32(hex: String): String {
    val crcCalc = CRC32()
    val byteArray = ByteArray(hex.length / 2)
    for (i in byteArray.indices) {
        val index = i * 2
        val j = hex.substring(index, index + 2).toInt(16)
        byteArray[i] = j.toByte()
    }
    crcCalc.update(byteArray)
    return "%08X".format(crcCalc.value)
}

// Function to convert an ascii string to crc32
fun asciiToCrc32(ascii: String): String {
    val hexVal = buildString {
        for (char in ascii) {
            append(String.format("%02X", char.code))
        }
    }
    val crcVal = hexToCrc32(hexVal)
    return crcVal
}

// Function to convert a list of ascii string to crc32
fun asciiToCrc32(values: List<String>): String {
    val combinedVal = values.joinToString("").uppercase(Locale.US)
    val hexVal = buildString {
        for (char in combinedVal) {
            append(String.format("%02X", char.code))
        }
    }
    val crcVal = hexToCrc32(hexVal)
    return crcVal
}



