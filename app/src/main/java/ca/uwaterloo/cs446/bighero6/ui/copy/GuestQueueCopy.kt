package ca.uwaterloo.cs446.bighero6.ui.copy

import java.util.concurrent.TimeUnit

/**
 * Shared guest-facing queue copy so StationInfoScreen and MyWaitlistsScreen stay aligned.
 *
 * Keep this file free of Compose and Firebase so it can be reused anywhere.
 */
object GuestQueueCopy {
    data class NearFrontCopy(
        val text: String,
    )

    data class YourTurnCopy(
        val text: String,
    )

    data class InLineCopy(
        val positionText: String,
        val estimatedWaitText: String?,
    )

    fun hiddenInLine(): String = "You're in line."

    /**
     * Used for the "first in line but manual notification and not reserved/notify yet" case.
     */
    fun nearFrontManual(
        operatorManagesSessionsOnly: Boolean,
    ): NearFrontCopy {
        return if (operatorManagesSessionsOnly) {
            NearFrontCopy("You’re near the front. The host will notify you when your table is ready.")
        } else {
            NearFrontCopy("You’re near the front. You’ll be notified when the station is ready.")
        }
    }

    /**
     * Used for the "Your turn" case (reserved/notify already happened and user is not in an active session).
     */
    fun yourTurn(
        operatorManagesSessionsOnly: Boolean,
        notificationMode: String,
    ): YourTurnCopy {
        // notificationMode check mainly exists to ensure future-proofing; StationInfoScreen/MyWaitlistsScreen
        // already call the right branch.
        val isManual = notificationMode == "manual"
        if (!isManual) {
            // self-serve defaults
            return YourTurnCopy("Your turn! Go to the machine and tap the NFC tag to start your session.")
        }

        return if (operatorManagesSessionsOnly) {
            YourTurnCopy("Your turn! Please return to the host stand to be seated.")
        } else {
            YourTurnCopy("Your turn! Go to the station to start your session.")
        }
    }

    fun notifiedWhenReady(operatorManagesSessionsOnly: Boolean): String {
        return if (operatorManagesSessionsOnly) {
            "You will be notified when your table is ready."
        } else {
            "You will be notified when the station is ready."
        }
    }

    fun stationAvailable(autoJoinEnabled: Boolean, operatorManagesSessionsOnly: Boolean): String? {
        // StationInfo shows this only for autoJoin-enabled idle.
        if (!autoJoinEnabled) return null
        return if (operatorManagesSessionsOnly) {
            null
        } else {
            "Station is available. Tap the NFC tag at the machine to start."
        }
    }

    /**
     * Computes the estimated wait time string for a guest in the queue.
     * Pass [currentSessionExpiresAtMillis] from the active session's expiresAt (or null).
     * Returns e.g. "5 min" or "0 min".
     */
    fun estimatedWait(
        position: Int,
        sessionDurationSeconds: Int,
        currentSessionExpiresAtMillis: Long?,
    ): String {
        val perSessionMinutes = TimeUnit.SECONDS.toMinutes(
            sessionDurationSeconds.toLong()
        ).toInt().coerceAtLeast(1)

        val remainingMinutes = if (currentSessionExpiresAtMillis != null) {
            val remainingMillis = currentSessionExpiresAtMillis - System.currentTimeMillis()
            if (remainingMillis > 0) {
                TimeUnit.MILLISECONDS.toMinutes(remainingMillis).toInt().coerceAtLeast(0)
            } else {
                0
            }
        } else {
            0
        }

        val peopleAhead = (position - 1).coerceAtLeast(0)
        val totalMinutes = remainingMinutes + peopleAhead * perSessionMinutes
        return if (totalMinutes <= 0) "0 min" else "$totalMinutes min"
    }

    /**
     * Guest-facing copy while in the queue (positions visible).
     */
    fun inLine(
        position: Int,
        estimatedWaitTime: String,
        showEstimatedWait: Boolean = position > 1,
    ): InLineCopy {
        return InLineCopy(
            positionText = "You're #$position in line",
            estimatedWaitText = if (showEstimatedWait && estimatedWaitTime.isNotEmpty()) {
                "Estimated wait: $estimatedWaitTime"
            } else {
                null
            },
        )
    }
}

