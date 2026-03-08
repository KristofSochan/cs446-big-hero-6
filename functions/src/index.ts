/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {setGlobalOptions} from "firebase-functions";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {onTaskDispatched} from "firebase-functions/v2/tasks";
import {getFunctions} from "firebase-admin/functions";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

// Initialize Firebase Admin
admin.initializeApp();

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
// us-east4 (Northern Virginia) - lower latency for Waterloo, ON
const REGION = "us-east4";

setGlobalOptions({maxInstances: 10, region: REGION});

import {onDocumentUpdated} from "firebase-functions/v2/firestore";

/**
 * Triggered when a station document is updated
 * - When session starts: Enqueue Cloud Task to expire it at expiresAt time
 * - When session ends: Notify next person in line
 */
export const onStationUpdate = onDocumentUpdated(
  {document: "stations/{stationId}", region: REGION},
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    const stationId = event.params.stationId;

    if (!before || !after) return;

    const beforeSession = before.currentSession;
    const afterSession = after.currentSession;

    // Case 0: New session (userId only). Set startedAt/expiresAt from server.
    if (afterSession?.userId && !afterSession.startedAt) {
      const now = admin.firestore.Timestamp.now();
      const isTimed = after.mode === "timed";
      const durationSec = after.sessionDurationSeconds ?? 900;
      const update: Record<string, unknown> = {
        "currentSession.startedAt": now,
      };
      if (isTimed) {
        update["currentSession.expiresAt"] = new admin.firestore.Timestamp(
          now.seconds + durationSec,
          now.nanoseconds
        );
      }
      const docRef = event.data?.after?.ref;
      if (docRef) await docRef.update(update);
      return;
    }

    // Case 1: Session has expiresAt - schedule expiration task
    if (afterSession?.expiresAt && !beforeSession?.expiresAt) {
      logger.info(
        `Session started for station ${stationId}, scheduling expiration`
      );
      await scheduleSessionExpiration(stationId, afterSession.expiresAt);
    }

    // Case 2: Session was cleared (expired or ended) - notify next person
    if (beforeSession && !afterSession) {
      logger.info(`Session cleared for station ${stationId}`);

      // Get attendees map values and find person at position 1
      const attendeesMap = after.attendees || {};
      const attendees = Object.values(attendeesMap) as Array<{
        status: string;
        joinedAt: admin.firestore.Timestamp;
        userId: string;
      }>;
      const waitingAttendees = attendees
        .filter((a) => a.status === "waiting")
        .sort((a, b) => a.joinedAt.toMillis() - b.joinedAt.toMillis());

      if (waitingAttendees.length > 0) {
        const nextUserId = waitingAttendees[0].userId;
        await notifyUserAtPositionOne(nextUserId, stationId, after.name);
      }
    }
  }
);

/**
 * Schedules a Cloud Task to expire a session at the specified time
 * @param {string} stationId - The station ID
 * @param {admin.firestore.Timestamp} expiresAt - When the session expires
 */
async function scheduleSessionExpiration(
  stationId: string,
  expiresAt: admin.firestore.Timestamp
) {
  try {
    const queue = getFunctions().taskQueue("expireSession");
    const targetUri = await getFunctionUrl("expireSession");

    const scheduleTime = expiresAt.toDate();

    await queue.enqueue(
      {stationId},
      {
        scheduleTime: scheduleTime,
        dispatchDeadlineSeconds: 60 * 5, // 5 minutes max
        uri: targetUri,
      }
    );

    logger.info(
      `Scheduled expiration task for station ${stationId} at ${scheduleTime}`
    );
  } catch (error) {
    logger.error(
      `Error scheduling expiration task for station ${stationId}:`,
      error
    );
    // Don't throw - session will still expire via backup scheduled function
  }
}

/**
 * Helper to get function URL (needed for Cloud Tasks)
 * @param {string} functionName - The function name
 * @return {Promise<string>} The function URL
 */
async function getFunctionUrl(functionName: string): Promise<string> {
  const projectId = process.env.GCLOUD_PROJECT || admin.app().options.projectId;
  const region = process.env.FUNCTION_REGION || REGION;
  return `https://${region}-${projectId}.cloudfunctions.net/${functionName}`;
}

/**
 * Sends FCM notification to user at position 1
 * @param {string} userId - The user ID
 * @param {string} stationId - The station ID
 * @param {string} stationName - The station name
 */
async function notifyUserAtPositionOne(
  userId: string,
  stationId: string,
  stationName: string
) {
  try {
    // Get user's FCM token
    const userDoc = await admin.firestore()
      .collection("users")
      .doc(userId)
      .get();

    if (!userDoc.exists) {
      logger.warn(`User ${userId} not found`);
      return;
    }

    const fcmToken = userDoc.data()?.fcmToken;
    if (!fcmToken) {
      logger.info(
        `No FCM token for user ${userId} - skipping notification`
      );
      return;
    }

    // Send notification
    const message = {
      notification: {
        title: "It's Your Turn!",
        body: `You're next in line for ${stationName}. ` +
          "Tap the NFC tag to start.",
      },
      data: {
        stationId: stationId,
        type: "position_one",
      },
      token: fcmToken,
    };

    await admin.messaging().send(message);
    logger.info(
      `Notification sent to user ${userId} for station ${stationId}`
    );
  } catch (error) {
    logger.error(`Error sending notification to user ${userId}:`, error);
  }
}

/**
 * Callable: returns server time and session expiry for elapsed-only countdown.
 * Client uses initialRemaining = (expiresAtMillis - serverTimeMillis) / 1000
 * then counts down by elapsed time only (no clock skew).
 */
export const getSessionTime = onCall(
  {region: REGION},
  async (request) => {
    const stationId = request.data?.stationId;
    if (!stationId || typeof stationId !== "string") {
      throw new HttpsError("invalid-argument", "stationId required");
    }
    const stationRef = admin.firestore()
      .collection("stations")
      .doc(stationId);
    const stationDoc = await stationRef.get();
    if (!stationDoc.exists) {
      throw new HttpsError("not-found", "Station not found");
    }
    const session = stationDoc.data()?.currentSession;
    const expiresAt = session?.expiresAt;
    const serverNow = admin.firestore.Timestamp.now();
    if (!expiresAt) {
      return {expiresAtMillis: null, serverTimeMillis: serverNow.toMillis()};
    }
    return {
      expiresAtMillis: expiresAt.toMillis(),
      serverTimeMillis: serverNow.toMillis(),
    };
  }
);

/**
 * Cloud Task handler that expires a session
 * Scheduled precisely when session.expiresAt time is reached
 */
export const expireSession = onTaskDispatched(
  {
    region: REGION,
    retryConfig: {
      maxAttempts: 3,
      minBackoffSeconds: 10,
    },
    rateLimits: {
      maxConcurrentDispatches: 10,
    },
  },
  async (req) => {
    const {stationId} = req.data as {"stationId": string};
    const db = admin.firestore();

    try {
      const stationRef = db.collection("stations").doc(stationId);
      const stationDoc = await stationRef.get();

      if (!stationDoc.exists) {
        logger.warn(`Station ${stationId} not found`);
        return;
      }

      const station = stationDoc.data();
      const currentSession = station?.currentSession;

      // Check if session exists and is expired
      if (!currentSession || !currentSession.expiresAt) {
        logger.info(
          `Station ${stationId} has no active session - ` +
          "already expired or cleared"
        );
        return;
      }

      const now = admin.firestore.Timestamp.now();
      if (currentSession.expiresAt.toMillis() > now.toMillis()) {
        // Not expired yet (shouldn't happen, but handle gracefully)
        const expiresAt = currentSession.expiresAt.toDate();
        logger.warn(
          `Session for station ${stationId} not yet expired ` +
          `(expires at ${expiresAt})`
        );
        return;
      }

      // Expire the session (onStationUpdate will trigger notification)
      await stationRef.update({
        currentSession: admin.firestore.FieldValue.delete(),
      });

      logger.info(`Expired session for station ${stationId}`);
    } catch (error) {
      logger.error(`Error expiring session for station ${stationId}:`, error);
      throw error;
    }
  }
);

