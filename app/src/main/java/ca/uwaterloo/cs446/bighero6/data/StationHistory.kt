package ca.uwaterloo.cs446.bighero6.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlin.math.max

@IgnoreExtraProperties
data class StationHistoryEvent(
    val time: Timestamp? = null,
    val type: String = ""
) {
    companion object {
        const val TYPE_JOIN = "JOIN"
        const val TYPE_LEAVE = "LEAVE"
        const val TYPE_START = "START"
        const val TYPE_END = "END"
    }
}

@IgnoreExtraProperties
data class StationHistory(
    val stationID: String = "",
    val ownerID: String = "",
    val history: List<StationHistoryEvent> = emptyList()
) {
    companion object {
        const val MIN_JOIN_LEAVE_DIFF = 2
    }

    // Calculates predicted number of seconds it takes to advance one position
    fun getPredictedSecondsPerPosition(): Int? {
        val filteredHistory = history.filter { it.time != null }.sortedBy { it.time }

        val intervals = mutableListOf<Long>()
        var numInQueue = 0
        var lastSessionTime : Timestamp? = null
        var lastJoinTime : Timestamp? = null
        for (sessionEvent in filteredHistory) {
            val currentTime = sessionEvent.time
            if (sessionEvent.type == StationHistoryEvent.TYPE_JOIN) {
                lastJoinTime = currentTime
                numInQueue++
            } else if (sessionEvent.type == StationHistoryEvent.TYPE_START) {
                if (numInQueue == 1) {
                    var startSeconds = Long.MIN_VALUE
                    if (lastSessionTime != null) {
                        startSeconds = max(startSeconds, lastSessionTime.seconds)
                    }
                    if (lastJoinTime != null) {
                        startSeconds = max(startSeconds, lastJoinTime.seconds)
                    }
                    if (currentTime != null && startSeconds != Long.MIN_VALUE) {
                        val diff = currentTime.seconds - startSeconds
                        assert(diff >= 0)
                        intervals.add(diff)
                    }
                } else {
                    assert(numInQueue > 1)
                    if (lastSessionTime != null && currentTime != null) {
                        val diff = currentTime.seconds - lastSessionTime.seconds
                        assert(diff >= 0)
                        intervals.add(diff)
                    }
                }
                lastSessionTime = currentTime
                numInQueue--
            } else if (sessionEvent.type == StationHistoryEvent.TYPE_LEAVE) {
                numInQueue--
            }
        }

        if (intervals.isEmpty()) return null

        // Most recent intervals first
        val recentIntervals = intervals.reversed()
        val baseWeights = listOf(0.2, 0.15, 0.1, 0.1, 0.1, 0.1, 0.1, 0.05, 0.05, 0.05)
        
        val numToUse = minOf(recentIntervals.size, baseWeights.size)
        val weightsToUse = baseWeights.take(numToUse)
        val weightSum = weightsToUse.sum()

        var weightedSum = 0.0
        for (i in 0 until numToUse) {
            weightedSum += recentIntervals[i] * (weightsToUse[i] / weightSum)
        }

        return weightedSum.toInt()
    }

    // Calculate average number of spots needed to wait (based on history of people leaving b4 turn)
    fun getPredictedNumSpots(currentPos: Int): Int? {
        var numJoins = 0
        var numLeaves = 0
        for (event in history) {
            if (event.type == StationHistoryEvent.TYPE_JOIN) {
                numJoins++
            } else if (event.type == StationHistoryEvent.TYPE_LEAVE) {
                numLeaves++
            }
        }
        assert(numJoins >= numLeaves)

        if (numJoins == 0) {
            return null // Unknown
        }

        if (numJoins - numLeaves < MIN_JOIN_LEAVE_DIFF) {
            numLeaves = max(0, numJoins - MIN_JOIN_LEAVE_DIFF)
        }

        val numerator = (numJoins - numLeaves) * currentPos
        val denominator = numJoins

        // Ceiling division
        return (numerator + denominator - 1) / denominator
    }

    fun getPredictedWaitTimeSeconds(currentPos: Int): Int? {
        val numSpots = getPredictedNumSpots(currentPos) // minus 1 cuz it's supposed to be # before
        val secondsPerPosition = getPredictedSecondsPerPosition()
        if (numSpots == null || secondsPerPosition == null) {
            return null
        }
        assert(numSpots >= 0 && secondsPerPosition >= 0)
        return numSpots * secondsPerPosition
    }

    fun getAverageSessionTimeSeconds(): Int? {
        val filteredHistory = history.filter { it.time != null }.sortedBy { it.time }
        val sessionTimes = mutableListOf<Long>()
        var activeStartTime: Timestamp? = null
        
        for (event in filteredHistory) {
            if (event.type == StationHistoryEvent.TYPE_START) {
                activeStartTime = event.time
            } else if (event.type == StationHistoryEvent.TYPE_END && activeStartTime != null) {
                val duration = event.time!!.seconds - activeStartTime.seconds
                if (duration >= 0) {
                    sessionTimes.add(duration)
                }
                activeStartTime = null
            }
        }
        
        if (sessionTimes.isEmpty()) return null
        return sessionTimes.average().toInt()
    }
}
