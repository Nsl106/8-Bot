package org.team9432.discord.eightbot.database

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.team9432.discord.eightbot.config.Config

object Database {
    // Create a new client and connect to the server
    private val client = MongoClient.create(Config.getConfig().mongodb.url)
    val database = client.getDatabase(Config.getConfig().mongodb.databaseName)

    inline fun <reified T: Any> getCollection(name: String): MongoCollection<T> {
        return database.getCollection<T>(name)
    }
}