package com.expiryreminder.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Item(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val expiry: LocalDate,
    // Smallest reminder stage (in days before expiry) already shown for this item.
    val notifiedStage: Int = NOT_NOTIFIED,
) {
    fun daysLeft(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, expiry)

    companion object {
        const val NOT_NOTIFIED = Int.MAX_VALUE
    }
}

/** Items are kept in a single JSON file in the app's private storage. */
object ItemStore {
    private const val FILE_NAME = "items.json"

    @Synchronized
    fun load(context: Context): List<Item> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Item(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = o.getString("category"),
                    expiry = LocalDate.parse(o.getString("expiry")),
                    notifiedStage = o.optInt("notifiedStage", Item.NOT_NOTIFIED),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(context: Context, items: List<Item>) {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("expiry", item.expiry.toString())
                    .put("notifiedStage", item.notifiedStage)
            )
        }
        // Write to a temp file first so a crash mid-write can't corrupt the list.
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
