package data

import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*

class DataFile(name: String, directory: String = "") {
    private val properties = Properties()
    private val file: File

    private val spacerChar = ','

    init {
        val dir = File("data/${directory.lowercase()}")
        dir.mkdirs()

        file = File(dir, "${name.lowercase()}.properties")
        file.createNewFile()

        val reader = FileReader(file)
        properties.load(reader)
        reader.close()
    }

    fun set(key: String, vararg value: String) {
        val realValue = StringBuilder()
        for (it in value) realValue.append("$it$spacerChar")

        properties.setProperty(key, realValue.toString())
        save()
    }


    fun remove(key: String) {
        properties.remove(key)
        save()
    }

    operator fun get(key: String): List<String>? = properties.getProperty(key)?.split(spacerChar)?.dropLast(1)

    fun getAllKeys() = properties.keys.map { it.toString() }

    fun containsKey(key: String) = properties.containsKey(key)

    //TODO: fun containsValue(value: String): Boolean {}

    fun getEntries(): Set<Pair<String, List<String>>> {
        return properties.entries.map { it.key.toString() to it.value.toString().split(spacerChar).dropLast(1) }.toSet()
    }


    private fun save() {
        //TODO: Separate save so it doesn't happen after each set/remove
        val writer = FileWriter(file)
        properties.store(writer, null)
        writer.close()
    }
}