package data

import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*

/*
TODO Have a way to separate values after getting them with a key
TODO The boring way would be to have .componentOne() .componentTwo(), etc.
TODO The best way would be data.get("key").getComponent("anotherkeything")
TODO OR, because data files seem to be used pretty frequently, it could probably be an array or something
TODO oh and it also needs a way to data.set("key", vararg)? maybe?
 */
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

    fun getAllKeys(): Iterator<Any> = properties.keys().asIterator()

    fun containsKey(key: String) = properties.containsKey(key)

    fun getEntries(): Set<Pair<String, List<String>>> {
        val newEntries = mutableSetOf<Pair<String, List<String>>>()
        val entries = properties.entries

        for (entry in entries) {
            val newValue = entry.value.toString().split(spacerChar).dropLast(1)
            val newKey = entry.key.toString()

            newEntries.add(newKey to newValue)
        }

        return newEntries
    }


    private fun save() {
        //TODO: Separate save so it doesn't happen after each set/remove
        val writer = FileWriter(file)
        properties.store(writer, null)
        writer.close()
    }
}