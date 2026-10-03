package com.novaguard.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DataManager {
    private val map = ConcurrentHashMap<UUID, PlayerData>()

    fun get(uuid: UUID): PlayerData = map.computeIfAbsent(uuid) { PlayerData(uuid) }

    fun remove(uuid: UUID) { map.remove(uuid) }

    fun decayVls(amount: Double) {
        for (data in map.values) data.decay(amount)
    }

    fun clear() = map.clear()
}
