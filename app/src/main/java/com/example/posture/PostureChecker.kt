package com.example.posture

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class PostureIssue(val advice: String) {
    SHOULDERS_UNEVEN("Плечи перекошены. Опустите плечи и выровняйте их."),
    HEAD_TILT("Голова наклонена набок. Держите голову прямо."),
    HEAD_OFFSET("Голова смещена в сторону. Выровняйте её по центру тела."),
    SLOUCH("Вы сутулитесь. Расправьте плечи и вытяните макушку вверх."),
    TOO_CLOSE("Вы слишком наклонились к экрану. Отодвиньтесь и обопритесь на спинку.")
}

data class Metrics(
    val shoulderTilt: Double,
    val headTilt: Double,
    val headOffset: Float,
    val neckRatio: Float,
    val widthRatio: Float
)

object PostureChecker {

    fun metrics(pose: Pose, imageWidth: Int): Metrics? {
        fun lm(type: Int) = pose.getPoseLandmark(type)?.takeIf { it.inFrameLikelihood > 0.6f }

        val ls = lm(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rs = lm(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val le = lm(PoseLandmark.LEFT_EAR) ?: return null
        val re = lm(PoseLandmark.RIGHT_EAR) ?: return null
        val nose = lm(PoseLandmark.NOSE) ?: return null

        val shoulderWidth = hypot(
            ls.position.x - rs.position.x,
            ls.position.y - rs.position.y
        )
        if (shoulderWidth < 1f) return null

        val shMidX = (ls.position.x + rs.position.x) / 2
        val shMidY = (ls.position.y + rs.position.y) / 2
        val earMidY = (le.position.y + re.position.y) / 2

        fun tilt(a: PoseLandmark, b: PoseLandmark) = Math.toDegrees(
            atan2(
                abs(a.position.y - b.position.y).toDouble(),
                abs(a.position.x - b.position.x).toDouble()
            )
        )

        return Metrics(
            shoulderTilt = tilt(ls, rs),
            headTilt = tilt(le, re),
            headOffset = abs(nose.position.x - shMidX) / shoulderWidth,
            neckRatio = (shMidY - earMidY) / shoulderWidth,
            widthRatio = shoulderWidth / imageWidth
        )
    }

    fun check(m: Metrics, baseline: Metrics?): List<PostureIssue> {
        val issues = mutableListOf<PostureIssue>()

        if (m.shoulderTilt > 7) issues += PostureIssue.SHOULDERS_UNEVEN
        if (m.headTilt > 10) issues += PostureIssue.HEAD_TILT
        if (m.headOffset > 0.30f) issues += PostureIssue.HEAD_OFFSET

        if (baseline != null) {
            if (m.neckRatio < baseline.neckRatio * 0.80f) issues += PostureIssue.SLOUCH
            if (m.widthRatio > baseline.widthRatio * 1.20f) issues += PostureIssue.TOO_CLOSE
        }
        return issues
    }
}