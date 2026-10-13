package com.kang.colorblockjam

import android.content.Context
import android.content.SharedPreferences

/** Persistent player progress. Lives are unlimited for now (see [LIVES_UNLIMITED]). */
class Progress(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    companion object {
        /** Switch to false later to enable a limited-lives economy; the game loop already reports failures. */
        const val LIVES_UNLIMITED = true
        const val COINS_PER_LEVEL = 40
        const val FREEZE_UNLOCK_LEVEL = 6
        const val HAMMER_UNLOCK_LEVEL = 8
        const val FREEZE_COST = 200
        const val HAMMER_COST = 300
        const val CONTINUE_COST = 300
        const val FREEZE_SECONDS = 10f
        const val CONTINUE_SECONDS = 20f
    }

    var highestUnlocked: Int
        get() = prefs.getInt("highest", 1)
        set(v) = prefs.edit().putInt("highest", v).apply()

    var coins: Int
        get() = prefs.getInt("coins", 400)
        set(v) = prefs.edit().putInt("coins", v).apply()

    var freezeCount: Int
        get() = prefs.getInt("freeze", 2)
        set(v) = prefs.edit().putInt("freeze", v).apply()

    var hammerCount: Int
        get() = prefs.getInt("hammer", 1)
        set(v) = prefs.edit().putInt("hammer", v).apply()

    var lastPlayed: Int
        get() = prefs.getInt("last", 1)
        set(v) = prefs.edit().putInt("last", v).apply()

    fun onLevelWon(level: Int) {
        coins += COINS_PER_LEVEL
        if (level >= highestUnlocked) highestUnlocked = level + 1
    }
}
