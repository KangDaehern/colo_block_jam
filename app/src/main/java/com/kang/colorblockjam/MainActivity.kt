package com.kang.colorblockjam

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import com.kang.colorblockjam.model.Levels
import com.kang.colorblockjam.ui.GameView
import com.kang.colorblockjam.ui.HomeView

/** Single activity that swaps between the home screen and the game view. */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var progress: Progress
    private var gameView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = Progress(this)
        root = FrameLayout(this)
        setContentView(root)
        // Debug/test hook: `adb shell am start -n com.kang.colorblockjam/.MainActivity --ei level 42` jumps straight into a level.
        val requested = intent?.getIntExtra("level", 0) ?: 0
        when {
            requested in 1..Levels.MAX_LEVEL -> startLevel(requested)
            // Normal launch: resume directly in the level the player was last in (the home screen stays reachable via Pause > Home).
            else -> startLevel(progress.lastPlayed.coerceIn(1, Levels.MAX_LEVEL))
        }
    }

    fun showHome() {
        gameView?.stop()
        gameView = null
        root.removeAllViews()
        root.addView(HomeView(this, progress) { level -> startLevel(level) })
    }

    fun startLevel(level: Int) {
        val n = level.coerceIn(1, Levels.MAX_LEVEL)
        progress.lastPlayed = n
        gameView?.stop()
        root.removeAllViews()
        val gv = GameView(this, progress, Levels.get(n),
            onHome = { showHome() },
            onNext = { startLevel(n + 1) },
            onRetry = { startLevel(n) })
        gameView = gv
        root.addView(gv)
    }

    override fun onPause() {
        super.onPause()
        gameView?.pauseGame()
    }

    override fun onDestroy() {
        gameView?.stop()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameView != null) showHome() else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
