package data

import com.google.gson.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class JsonFile(name: String, directory: String = "") {
    private val gson: Gson
    private val file: File
    private val mainObject: JsonObject

    init {
        val dir = File("data/${directory.lowercase()}")
        dir.mkdirs()

        file = File(dir, "${name.lowercase()}.json")
        file.createNewFile()

        gson = GsonBuilder().setPrettyPrinting().create()

        if (file.length() == 0L) {
            val w = FileWriter(file)
            gson.toJson(JsonObject(), w)
            w.close()
        }
        val reader = FileReader(file)
        mainObject = gson.fromJson(reader, JsonObject().javaClass)
        reader.close()
    }

    val mainObj get() = mainObject

    fun save() {
        val w = FileWriter(file)
        gson.toJson(mainObject, w)
        w.close()
    }
}

fun JsonObject.asArrayMap(): Map<String, List<String>?> {
    return asMap().mapValues {
        if (it.value.isJsonArray) {
            it.value.asJsonArray.toList().map { e -> e.asJsonPrimitive.asString }
        } else {
            null
        }
    }
}

fun JsonObject.setObject(key: String, obj: JsonObject) {
    add(key, obj)
}

fun JsonObject.getObject(key: String): JsonObject? {
    val element = get(key) ?: return null
    if (!element.isJsonObject) return null
    return element.asJsonObject
}

fun JsonObject.getObjectOrNew(key: String): JsonObject {
    val existingObject = getObject(key)
    return if (existingObject != null) {
        existingObject
    } else {
        val newObject = JsonObject()
        setObject(key, newObject)
        newObject
    }
}

fun JsonObject.setValue(key: String, value: String) {
    val str = JsonPrimitive(value)
    add(key, str)
}

fun JsonObject.getValue(key: String): String? {
    val element = get(key) ?: return null
    if (!element.isJsonPrimitive) return null
    return element.asJsonPrimitive.asString
}

fun JsonObject.setArray(key: String, values: List<String>) {
    val array = JsonArray()
    values.forEach { array.add(it) }
    add(key, array)
}

fun JsonObject.setArray(key: String, array: JsonArray) {
    add(key, array)
}

fun JsonObject.getArrayOrNew(key: String): JsonArray {
    val existingArray = getArray(key)
    return if (existingArray != null) {
        existingArray
    } else {
        val newArray = JsonArray()
        setArray(key, newArray)
        newArray
    }
}

fun JsonObject.getArray(key: String): JsonArray? {
    val element = get(key) ?: return null
    if (!element.isJsonArray) return null
    return element.asJsonArray
}

fun JsonObject.setProperty(key: String, value: String) {
    addProperty(key, value)
}

fun JsonObject.getAsMap(): Map<String, String> {
    return asMap().mapValues { it.value.asJsonPrimitive.asString }
}

fun JsonObject.setProperties(values: Map<String, String>) {
    values.forEach { setProperty(it.key, it.value) }
}

val JsonObject.keys get() = keySet().toSet()

fun JsonObject.containsKey(key: String) = keys.contains(key)

