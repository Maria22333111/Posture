package com.example.posture

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.google.mlkit.vision.pose.Pose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiState(
    val personVisible: Boolean = false,
    val issues: List<PostureIssue> = emptyList(),
    val calibrated: Boolean = false
)

class PostureViewModel(app: Application) : AndroidViewModel(app) {

    private val notifier = Notifier(app)
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var baseline: Metrics? = null
    private var lastMetrics: Metrics? = null
    private var badSince = 0L
    private var lastAlert = 0L

    private val badDurationMs = 3_000L
    private val cooldownMs = 15_000L

    fun onPose(pose: Pose?, imageWidth: Int) {
        val m = pose?.let { PostureChecker.metrics(it, imageWidth) }
        lastMetrics = m

        if (m == null) {
            badSince = 0
            _state.update { it.copy(personVisible = false, issues = emptyList()) }
            return
        }

        val issues = PostureChecker.check(m, baseline)
        val now = SystemClock.elapsedRealtime()

        if (issues.isEmpty()) {
            badSince = 0
        } else {
            if (badSince == 0L) badSince = now
            if (now - badSince >= badDurationMs && now - lastAlert >= cooldownMs) {
                lastAlert = now
                notifier.alert(issues.first().advice)
            }
        }
        _state.update { it.copy(personVisible = true, issues = issues) }
    }

    /** Запомнить текущее положение как правильное */
    fun calibrate() {
        lastMetrics?.let {
            baseline = it
            _state.update { s -> s.copy(calibrated = true) }
        }
    }

    override fun onCleared() {
        notifier.shutdown()
    }
}