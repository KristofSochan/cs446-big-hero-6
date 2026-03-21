package ca.uwaterloo.cs446.bighero6.ui.copy

import java.util.concurrent.TimeUnit

/**
 * Shared guest-facing queue copy so StationInfoScreen and MyWaitlistsScreen stay aligned.
 *
 * Keep this file free of Compose and Firebase so it can be reused anywhere.
 */
object GuestQueueCopy {
    data class StatusInfo(
        val primaryText: String,
        val secondaryText: String? = null,
        val isPrimaryHighlighted: Boolean = false,
    )

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
        return NearFrontCopy(notifiedWhenReady(operatorManagesSessionsOnly))
    }

    /**
     * Used for the "Your turn" case (reserved/notify already happened and user is not in an active session).
     * These strings are usually used as secondary text under a "Your turn!" header.
     */
    fun yourTurn(
        operatorManagesSessionsOnly: Boolean,
        notificationMode: String,
    ): YourTurnCopy {
        val isManual = notificationMode == "manual"
        if (!isManual) {
            return YourTurnCopy("Go to the machine and tap the NFC tag to start your session.")
        }

        return if (operatorManagesSessionsOnly) {
            YourTurnCopy("Please return to the host stand to be seated.")
        } else {
            YourTurnCopy("Go to the station to start your session.")
        }
    }

    fun notifiedWhenReady(operatorManagesSessionsOnly: Boolean): String {
        return "You will be notified when it’s your turn."
    }

    fun stationAvailable(autoJoinEnabled: Boolean, operatorManagesSessionsOnly: Boolean): String? {
        if (!autoJoinEnabled) return null
        return if (operatorManagesSessionsOnly) {
            null
        } else {
            "Station is available. Tap the NFC tag at the machine to start."
        }
    }

    /**
     * Computes the estimated wait time string for a guest in the queue.
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

    /**
     * Main entry point for user-specific status text to ensure consistency across screens.
     */
    fun getStatus(
        position: Int, // Real position (1-based)
        showPositionToGuests: Boolean,
        hasReservation: Boolean,
        hasActiveSession: Boolean,
        isInSession: Boolean,
        isManualNotification: Boolean,
        operatorManagesSessionsOnly: Boolean,
        estimatedWaitTime: String = "",
        stationName: String = ""
    ): StatusInfo {
        if (isInSession) {
            return StatusInfo(
                primaryText = if (stationName.isNotEmpty()) {
                    "You're currently using $stationName"
                } else {
                    "You're currently using this station"
                }
            )
        }

        // Case: Your turn (notified/reserved but session not started yet)
        if (hasReservation && !hasActiveSession) {
            return StatusInfo(
                primaryText = "Your turn!",
                secondaryText = yourTurn(
                    operatorManagesSessionsOnly,
                    if (isManualNotification) "manual" else "auto"
                ).text,
                isPrimaryHighlighted = true
            )
        }

        // If positions are hidden, show a consistent message regardless of relative position.
        // This is important for "manned" mode where the operator might seat people out of order.
        if (!showPositionToGuests) {
            return if (isManualNotification) {
                StatusInfo(primaryText = notifiedWhenReady(operatorManagesSessionsOnly))
            } else {
                StatusInfo(primaryText = hiddenInLine())
            }
        }

        // Case: Next in line (Position 1) - Positions ARE shown here
        if (position == 1) {
            return StatusInfo(
                primaryText = "You're next in line",
                secondaryText = notifiedWhenReady(operatorManagesSessionsOnly)
            )
        }

        // Case: Default in line with position shown
        val lineInfo = inLine(position, estimatedWaitTime)
        return StatusInfo(
            primaryText = lineInfo.positionText,
            secondaryText = lineInfo.estimatedWaitText
        )
    }
}
