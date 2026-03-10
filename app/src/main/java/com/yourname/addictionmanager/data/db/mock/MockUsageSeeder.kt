package com.yourname.addictionmanager.data.mock

import com.yourname.addictionmanager.data.db.UsageDao
import com.yourname.addictionmanager.data.db.UsageEntity

object MockUsageSeeder {

    suspend fun seed(dao: UsageDao, date: String) {

        // 🔒 Check database instead of memory
        val existingCount = dao.getUsageCountForDate(date)
        if (existingCount > 0) return

        val data = listOf(
            UsageEntity(
                appName = "Instagram",
                minutesUsed = 130,
                date = date
            ),
            UsageEntity(
                appName = "Call of Duty",
                minutesUsed = 105,
                date = date
            ),
            UsageEntity(
                appName = "WhatsApp",
                minutesUsed = 72,
                date = date
            ),
            UsageEntity(
                appName = "Clash Royale",
                minutesUsed = 38,
                date = date
            )
        )

        dao.insertAll(data)
    }
}
