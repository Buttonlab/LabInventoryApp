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
        if (!arrayListOf("31", "32", "33", "34", "35", "66").contains(hex.substring(0,2))) {
            return ""
        }

        val type = hexToAscii(hexIn.substring(0,2))
        if (type == "1" || type == "3") { // Primary cells
            val toConvert = hexIn.dropLast(4)
            val unique = hexIn.takeLast(4)
            return hexToAscii(toConvert) +
                    unique
        } else if (type == "2" || type == "4") { // Immortal cells
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
        }  else if (type == "5") { // Frozen Other
            val toConvert1 = hexIn.substring(0,14)
            val clone = hexIn.substring(14, 16)
            val toConvert2 = hexIn.substring(16, 20)
            val unique = hexIn.substring(20)
            return hexToAscii(toConvert1) +
                    clone +
                    hexToAscii(toConvert2) +
                    unique
        }  else if (hex.startsWith("66")) { // Basic items
            return hexIn
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
        } else if (type == "5") {
            val toConvert1 = asciiStr.substring(0, 7)
            val clone = asciiStr.substring(7, 9)
            val toConvert2 = asciiStr.substring(9, 11)
            val unique = asciiStr.substring(11)
            return asciiToHex(toConvert1) +
                    clone +
                    asciiToHex(toConvert2) +
                    unique
        }  else if (type == "6") {
            return asciiStr
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
    var hexVar = ""
    hexVar = if (ascii.length == 24) { ascii } else { asciiToHex(ascii) }

    return hexToCrc32(hexVar)
}

// Function to convert a list of ascii string to crc32
fun asciiToCrc32(values: List<String>): String {
    var hexString = ""
    values.forEach { hexString +=  if (it.length==24) { it } else { asciiToHex(it) } }

    return hexToCrc32(hexString)
}

// Function to check is ascii is correct length for one of the types
fun isCorrectLen(cellID: String): Boolean {
    return intArrayOf(14, 15, 16, 24).contains(cellID.length)
}

// Function to create the ascii representation of a cell item
fun reprCell(givenItem: CellItem): String {
    if (givenItem.type.equals("1") || givenItem.type.equals("3")) {
        return givenItem.type + givenItem.genotype + givenItem.distNum + givenItem.year + givenItem.owner + givenItem.passage + givenItem.surface + givenItem.number + givenItem.unique
    } else if (givenItem.type.equals("2") || givenItem.type.equals("4")) {
        return givenItem.type + givenItem.cellType + givenItem.genemod + givenItem.gene1 + givenItem.gene2 + givenItem.resistance + givenItem.clone + givenItem.passage + givenItem.surface + givenItem.number + givenItem.unique
    } else if (givenItem.type.equals("5")) {
        return givenItem.type + givenItem.otherType + givenItem.otherGenemod + givenItem.gene1 + givenItem.gene2 + givenItem.primaryResistance + givenItem.vectorResistance + givenItem.clone + "-" + givenItem.number + givenItem.unique
    } else if (givenItem.type.equals("6")) {
        return givenItem.id ?: ""
    }
    return ""
}

fun cellFromEPC(cellID: String): CellItem {
    val foundParts = mutableMapOf<String, String>()
    if (cellID.startsWith("1") || cellID.startsWith("3")) {
        // Add the parts of a primary cell
        foundParts.put("id", cellID.substring(cellID.length-7))
        foundParts.put("type", cellID.substring(0, 1))
        foundParts.put("genotype", cellID.substring(1, 2))
        foundParts.put("distNum", cellID.substring(2, 4))
        foundParts.put("year", cellID.substring(4, 6))
        foundParts.put("owner", cellID.substring(6, 7))
        foundParts.put("passage", cellID.substring(7, 8))
        foundParts.put("surface", cellID.substring(8, 9))
        foundParts.put("number", cellID.substring(9, 10))
        foundParts.put("unique", cellID.substring(10))
    } else if (cellID.startsWith("2") || cellID.startsWith("4")) {
        // Add the parts of an immortal cell
        foundParts.put("id", cellID.substring(cellID.length-7))
        foundParts.put("type", cellID.substring(0, 1))
        foundParts.put("cellType", cellID.substring(1, 2))
        foundParts.put("genemod", cellID.substring(2, 3))
        foundParts.put("gene1", cellID.substring(3, 4))
        foundParts.put("gene2", cellID.substring(4, 5))
        foundParts.put("resistance", cellID.substring(5, 6))
        foundParts.put("clone", cellID.substring(6, 8))
        foundParts.put("passage", cellID.substring(8, 10))
        foundParts.put("surface", cellID.substring(10, 11))
        foundParts.put("number", cellID.substring(11, 12))
        foundParts.put("unique", cellID.substring(12))
    } else if (cellID.startsWith("5")) {
        // Add the parts of a frozen bacteria
        foundParts.put("id", cellID.substring(cellID.length-8))
        foundParts.put("type", cellID.substring(0, 1))
        foundParts.put("otherType", cellID.substring(1, 2))
        foundParts.put("otherGenemod", cellID.substring(2, 3))
        foundParts.put("gene1", cellID.substring(3, 4))
        foundParts.put("gene2", cellID.substring(4, 5))
        foundParts.put("primaryResistance", cellID.substring(5, 6))
        foundParts.put("vectorResistance", cellID.substring(6, 7))
        foundParts.put("clone", cellID.substring(7, 9))
        foundParts.put("number", cellID.substring(10, 11))
        foundParts.put("unique", cellID.substring(11))
    } else if (cellID.startsWith("6")) {
        // Add the parts of a basic item
        foundParts.put("id", cellID)
        foundParts.put("type", "6")
    }
    return CellItem(
        id = foundParts.get("id"),
        type = foundParts.get("type"),
        genotype = foundParts.get("genotype"),
        distNum = foundParts.get("distNum"),
        year = foundParts.get("year"),
        owner = foundParts.get("owner"),
        cellType = foundParts.get("cellType"),
        genemod = foundParts.get("geneMod"),
        gene1 = foundParts.get("gene1"),
        gene2 = foundParts.get("gene2"),
        resistance = foundParts.get("resistance"),
        otherType = foundParts.get("otherType"),
        otherGenemod = foundParts.get("otherGenemod"),
        primaryResistance = foundParts.get("primaryResistance"),
        vectorResistance = foundParts.get("vectorResistance"),
        clone = foundParts.get("clone"),
        passage = foundParts.get("passage"),
        surface = foundParts.get("surface"),
        number = foundParts.get("number"),
        unique = foundParts.get("unique"))
}

