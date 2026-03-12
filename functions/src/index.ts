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
import {randomUUID} from "crypto";
import {Station as StationDoc, Attendee} from "./types";

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
 * Returns YYYY-MM-DD in UTC for analytics bucketing.
 * @param {admin.firestore.Timestamp} ts - Timestamp to bucket.
 * @return {string} The day key (UTC) in YYYY-MM-DD.
 */
function dateKeyUTC(ts: admin.firestore.Timestamp): string {
  const d = ts.toDate();
  const yyyy = d.getUTCFullYear();
  const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(d.getUTCDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * Returns `/stationAnalytics/{stationId}/daily/{yyyy-MM-dd}` doc ref.
 * @param {string} stationId - Station id.
 * @param {admin.firestore.Timestamp} ts - Timestamp to bucket.
 * @return {admin.firestore.DocumentReference} Analytics doc ref.
 */
function stationDailyAnalyticsRef(
  stationId: string,
  ts: admin.firestore.Timestamp,
) {
  const dayKey = dateKeyUTC(ts);
  return admin
    .firestore()
    .collection("stationAnalytics")
    .doc(stationId)
    .collection("daily")
    .doc(dayKey);
}

/**
 * Increment daily aggregates for a completed session start event.
 * Stores only counts/sums (no user ids).
 * @param {string} stationId - Station id.
 * @param {admin.firestore.Timestamp} startedAt - Session start time.
 * @param {number} waitSeconds - Wait time in seconds.
 */
async function incrementSessionAnalytics(
  stationId: string,
  startedAt: admin.firestore.Timestamp,
  waitSeconds: number,
): Promise<void> {
  const ref = stationDailyAnalyticsRef(stationId, startedAt);
  await ref.set(
    {
      totalSessions: admin.firestore.FieldValue.increment(1),
      totalWaitTimeSeconds: admin.firestore.FieldValue.increment(waitSeconds),
      totalNoShows: admin.firestore.FieldValue.increment(0),
    },
    {merge: true},
  );
}

/**
 * Transactional increment of daily no-show count.
 * @param {admin.firestore.Transaction} tx - Firestore transaction.
 * @param {string} stationId - Station id.
 * @param {admin.firestore.Timestamp} ts - Timestamp to bucket.
 */
function analyticsIncrementNoShowTx(
  tx: admin.firestore.Transaction,
  stationId: string,
  ts: admin.firestore.Timestamp,
) {
  const ref = stationDailyAnalyticsRef(stationId, ts);
  tx.set(
    ref,
    {
      totalSessions: admin.firestore.FieldValue.increment(0),
      totalWaitTimeSeconds: admin.firestore.FieldValue.increment(0),
      totalNoShows: admin.firestore.FieldValue.increment(1),
    },
    {merge: true},
  );
}

/**
 * Triggered when a station document is updated
 * - When session starts: Enqueue Cloud Task to expire it at expiresAt time
 * - When session ends: Notify next person in line
 */
export const onStationUpdate = onDocumentUpdated(
  {document: "stations/{stationId}", region: REGION},
  async (event) => {
    const before = event.data?.before.data() as StationDoc | undefined;
    const after = event.data?.after.data() as StationDoc | undefined;
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
        "currentSession.sessionId": randomUUID(),
      };
      if (isTimed) {
        update["currentSession.expiresAt"] = new admin.firestore.Timestamp(
          now.seconds + durationSec,
          now.nanoseconds,
        );
      }
      const docRef = event.data?.after?.ref;
      if (docRef) await docRef.update(update);
      return;
    }

    // Case 1: Session has expiresAt - schedule expiration task
    if (afterSession?.expiresAt && !beforeSession?.expiresAt) {
      logger.info(
        `Session started for station ${stationId}, scheduling expiration`,
      );
      const sessionId =
        typeof afterSession.sessionId === "string" ?
          afterSession.sessionId :
          null;
      if (!sessionId) {
        logger.error(
          `No currentSession.sessionId present for station ${stationId}; ` +
            "cannot schedule expireSession safely",
        );
        return;
      }
      logger.info(
        `Scheduling expireSession task for station ${stationId} ` +
          `(sessionId=${sessionId}, ` +
          `expiresAt=${afterSession.expiresAt.toDate()})`,
      );
      await scheduleSessionExpiration(
        stationId,
        afterSession.expiresAt,
        sessionId,
      );
    }

    // Case 2: Session was cleared (expired or ended) - notify next person
    if (beforeSession && !afterSession) {
      logger.info(`Session cleared for station ${stationId}`);

      await advanceQueue(stationId, after);
    }
  },
);

/**
 * Schedules a Cloud Task to expire a session at the specified time
 * @param {string} stationId - The station ID
 * @param {admin.firestore.Timestamp} expiresAt - When the session expires
 * @param {string} sessionId - The session id to expire
 */
async function scheduleSessionExpiration(
  stationId: string,
  expiresAt: admin.firestore.Timestamp,
  sessionId: string,
) {
  try {
    const queue = getFunctions().taskQueue(
      `locations/${REGION}/functions/expireSession`,
    );
    const targetUri = await getFunctionUrl("expireSession");

    const scheduleTime = expiresAt.toDate();

    await queue.enqueue(
      {stationId, sessionId},
      {
        scheduleTime: scheduleTime,
        dispatchDeadlineSeconds: 60 * 5, // 5 minutes max
        uri: targetUri,
      },
    );

    logger.info(
      `Scheduled expiration task for station ${stationId} at ${scheduleTime}`,
    );
  } catch (error) {
    logger.error(
      `Error scheduling expiration task for station ${stationId}:`,
      error,
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
 * Advances the queue for a station: notifies head of queue and, if
 * enforceCheckinLimit is enabled, creates a reservation window and
 * schedules its expiration.
 * @param {string} stationId - The station ID
 * @param {StationDoc} stationSnapshot - Latest station data
 */
async function advanceQueue(
  stationId: string,
  stationSnapshot?: StationDoc,
): Promise<void> {
  const db = admin.firestore();
  const stationRef = db.collection("stations").doc(stationId);

  const station: StationDoc | undefined =
    stationSnapshot ??
    ((await stationRef.get()).data() as StationDoc | undefined);

  if (!station) return;

  const attendeesMap = station.attendees || {};
  const attendees = Object.values(attendeesMap) as Attendee[];
  const waitingAttendees = attendees
    .filter((a) => a.status === "waiting")
    .sort((a, b) => a.joinedAt.toMillis() - b.joinedAt.toMillis());

  if (waitingAttendees.length === 0) {
    // No one waiting; clear any existing reservation.
    await stationRef
      .update({
        currentReservation: admin.firestore.FieldValue.delete(),
      })
      .catch((error) => {
        logger.error(
          `Failed to clear currentReservation for station ${stationId}`,
          error,
        );
      });
    return;
  }

  const nextUserId = waitingAttendees[0].userId;
  const now = admin.firestore.Timestamp.now();
  const joinedAt = waitingAttendees[0].joinedAt;
  const queueWaitSeconds = Math.max(
    0,
    Math.floor((now.toMillis() - joinedAt.toMillis()) / 1000),
  );

  // Analytics: "queue wait" is time until it's your turn
  // (excludes check-in delay).
  await incrementSessionAnalytics(stationId, now, queueWaitSeconds);

  await notifyUserAtPositionOne(nextUserId, stationId, station.name);

  if (!station.enforceCheckinLimit) {
    // No check-in window enforcement; nothing more to do.
    await stationRef
      .update({
        currentReservation: admin.firestore.FieldValue.delete(),
      })
      .catch((error) => {
        logger.error(
          `Failed to clear currentReservation for station ${stationId}`,
          error,
        );
      });
    return;
  }

  const checkinWindowSeconds = station.checkinWindowSeconds ?? 60;
  const expiresAt = new admin.firestore.Timestamp(
    now.seconds + checkinWindowSeconds,
    now.nanoseconds,
  );
  const reservationId = randomUUID();

  await stationRef.update({
    currentReservation: {
      userId: nextUserId,
      expiresAt,
      reservationId,
    },
  });

  await scheduleReservationExpiration(stationId, expiresAt, reservationId);
}

/**
 * Schedules a Cloud Task to expire a reservation at the specified time.
 * @param {string} stationId - The station ID
 * @param {admin.firestore.Timestamp} expiresAt - When the reservation expires
 * @param {string} reservationId - The reservation id to expire
 */
async function scheduleReservationExpiration(
  stationId: string,
  expiresAt: admin.firestore.Timestamp,
  reservationId: string,
) {
  try {
    const queue = getFunctions().taskQueue(
      `locations/${REGION}/functions/expireReservation`,
    );
    const targetUri = await getFunctionUrl("expireReservation");

    const scheduleTime = expiresAt.toDate();

    await queue.enqueue(
      {stationId, reservationId},
      {
        scheduleTime,
        dispatchDeadlineSeconds: 60 * 5,
        uri: targetUri,
      },
    );

    logger.info(
      `Scheduled reservation expiration for station ${stationId} ` +
        `at ${scheduleTime}`,
    );
  } catch (error) {
    logger.error(
      `Error scheduling reservation expiration for station ${stationId}:`,
      error,
    );
  }
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
  stationName: string,
) {
  try {
    // Get user's FCM token
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();

    if (!userDoc.exists) {
      logger.warn(`User ${userId} not found`);
      return;
    }

    const fcmToken = userDoc.data()?.fcmToken;
    if (!fcmToken) {
      logger.info(`No FCM token for user ${userId} - skipping notification`);
      return;
    }

    // Send notification
    const message = {
      notification: {
        title: "It's Your Turn!",
        body:
          `You're next in line for ${stationName}. ` +
          "Tap the NFC tag to start.",
      },
      data: {
        stationId: stationId,
        type: "position_one",
      },
      token: fcmToken,
    };

    await admin.messaging().send(message);
    logger.info(`Notification sent to user ${userId} for station ${stationId}`);
  } catch (error) {
    logger.error(`Error sending notification to user ${userId}:`, error);
  }
}

/**
 * Callable: returns server time and session expiry for elapsed-only countdown.
 * Client uses initialRemaining = (expiresAtMillis - serverTimeMillis) / 1000
 * then counts down by elapsed time only (no clock skew).
 */
export const getSessionTime = onCall({region: REGION}, async (request) => {
  const stationId = request.data?.stationId;
  if (!stationId || typeof stationId !== "string") {
    throw new HttpsError("invalid-argument", "stationId required");
  }
  const stationRef = admin.firestore().collection("stations").doc(stationId);
  const stationDoc = await stationRef.get();
  if (!stationDoc.exists) {
    throw new HttpsError("not-found", "Station not found");
  }
  const session = stationDoc.data()?.currentSession;
  const expiresAt = session?.expiresAt;
  const serverNow = admin.firestore.Timestamp.now();
  if (!expiresAt) {
    return {
      expiresAtMillis: null,
      serverTimeMillis: serverNow.toMillis(),
    };
  }
  return {
    expiresAtMillis: expiresAt.toMillis(),
    serverTimeMillis: serverNow.toMillis(),
  };
});

/**
 * Callable: returns server time and reservation expiry for check-in countdown.
 * Client uses initialRemainingMs =
 * reservationExpiresAtMillis - serverTimeMillis
 * then counts down by elapsed time only (same as session timer).
 */
export const getReservationTime = onCall(
  {region: REGION},
  async (request) => {
    const stationId = request.data?.stationId;
    if (!stationId || typeof stationId !== "string") {
      throw new HttpsError("invalid-argument", "stationId required");
    }
    const stationRef = admin.firestore().collection("stations").doc(stationId);
    const stationDoc = await stationRef.get();
    if (!stationDoc.exists) {
      throw new HttpsError("not-found", "Station not found");
    }
    const reservation = stationDoc.data()?.currentReservation;
    const expiresAt = reservation?.expiresAt;
    const serverNow = admin.firestore.Timestamp.now();
    if (!expiresAt) {
      return {
        reservationExpiresAtMillis: null,
        serverTimeMillis: serverNow.toMillis(),
      };
    }
    return {
      reservationExpiresAtMillis: expiresAt.toMillis(),
      serverTimeMillis: serverNow.toMillis(),
    };
  },
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
    const {stationId, sessionId} = req.data as {
      stationId: string;
      sessionId?: string;
    };
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
            "already expired or cleared",
        );
        return;
      }

      const currentSessionId =
        typeof currentSession.sessionId === "string" ?
          currentSession.sessionId :
          null;
      if (sessionId && currentSessionId && currentSessionId !== sessionId) {
        // Stale task from a previous session; ignore quietly.
        logger.info(
          `Ignoring stale expireSession task for station ${stationId} ` +
            `(taskSessionId=${sessionId}, ` +
            `currentSessionId=${currentSessionId})`,
        );
        return;
      }

      const now = admin.firestore.Timestamp.now();
      if (currentSession.expiresAt.toMillis() > now.toMillis()) {
        // Not expired yet. Can happen if this task is stale and a new session
        // is active, or if Cloud Tasks dispatch ran slightly early.
        return;
      }

      const sessionUserId =
        typeof currentSession.userId === "string" ?
          currentSession.userId :
          null;

      // Expire the session and clean up the user's waitlist entry.
      await db.runTransaction(async (tx) => {
        const latest = await tx.get(stationRef);
        if (!latest.exists) return;

        const latestSession = latest.data()?.currentSession;
        if (!latestSession || !latestSession.expiresAt) return;
        const latestSessionId =
          typeof latestSession.sessionId === "string" ?
            latestSession.sessionId :
            null;
        if (sessionId && latestSessionId && latestSessionId !== sessionId) {
          logger.info(
            "Ignoring stale expireSession task in transaction for station " +
              `${stationId} (taskSessionId=${sessionId}, ` +
              `currentSessionId=${latestSessionId ?? "null"})`,
          );
          return;
        }
        if (latestSession.expiresAt.toMillis() > now.toMillis()) return;

        tx.update(stationRef, {
          currentSession: admin.firestore.FieldValue.delete(),
        });

        const latestUserId =
          typeof latestSession.userId === "string" ?
            latestSession.userId :
            null;
        const userIdToUpdate = latestUserId ?? sessionUserId;
        if (userIdToUpdate) {
          const userRef = db.collection("users").doc(userIdToUpdate);
          tx.update(userRef, {
            currentWaitlists: admin.firestore.FieldValue.arrayRemove(stationId),
          });
        }
      });

      logger.info(
        `Expired session for station ${stationId} ` +
          `(sessionId=${sessionId ?? currentSessionId ?? "unknown"})`,
      );
    } catch (error) {
      logger.error(`Error expiring session for station ${stationId}:`, error);
      throw error;
    }
  },
);

/**
 * Cloud Task handler that expires a reservation window.
 * If the reserved user did not start a session in time,
 * they are removed and the queue advances.
 */
export const expireReservation = onTaskDispatched(
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
    const {stationId, reservationId} = req.data as {
      stationId: string;
      reservationId?: string;
    };
    const db = admin.firestore();

    try {
      const stationRef = db.collection("stations").doc(stationId);
      const stationDoc = await stationRef.get();

      if (!stationDoc.exists) {
        logger.warn(`Station ${stationId} not found (expireReservation)`);
        return;
      }

      const station = stationDoc.data() as StationDoc;

      // If a session already started, reservation is implicitly consumed.
      if (station.currentSession && station.currentSession.userId) {
        logger.info(
          `Station ${stationId} has active session; ` +
            "skipping reservation expiration",
        );
        return;
      }

      const reservation = station.currentReservation;

      if (!reservation) {
        logger.info(
          `Station ${stationId} has no currentReservation; ` +
            "nothing to expire",
        );
        return;
      }

      const currentReservationId =
        typeof reservation.reservationId === "string" ?
          reservation.reservationId :
          null;
      if (
        reservationId &&
        currentReservationId &&
        currentReservationId !== reservationId
      ) {
        // Stale task from a previous reservation; ignore quietly.
        return;
      }

      const now = admin.firestore.Timestamp.now();
      if (reservation.expiresAt.toMillis() > now.toMillis()) {
        // Not expired yet. Can happen if this task is stale and the reservation
        // was refreshed/replaced, or if Cloud Tasks dispatch ran early.
        return;
      }

      // Remove reserved user atomically if still reserved, then advance queue.
      await db.runTransaction(async (tx) => {
        const snap = await tx.get(stationRef);
        if (!snap.exists) return;
        const data = snap.data() as StationDoc;
        const currentReservation = data.currentReservation;
        const currentSession = data.currentSession;

        if (!currentReservation || currentSession?.userId) {
          return;
        }

        // Ensure we are still expiring the same reservation.
        if (
          (reservationId &&
            typeof currentReservation.reservationId === "string" &&
            currentReservation.reservationId !== reservationId) ||
          currentReservation.userId !== reservation.userId ||
          currentReservation.expiresAt.toMillis() !==
            reservation.expiresAt.toMillis()
        ) {
          return;
        }

        const updates: Record<string, unknown> = {
          currentReservation: admin.firestore.FieldValue.delete(),
        };

        if (data.attendees && data.attendees[reservation.userId]) {
          updates[`attendees.${reservation.userId}`] =
            admin.firestore.FieldValue.delete();
        }

        tx.update(stationRef, updates);
        analyticsIncrementNoShowTx(tx, stationId, now);
      });

      logger.info(
        `Expired reservation and removed user ${
          reservation.userId
        } from station ${stationId}`,
      );

      // Reload station and advance queue for next person, if any.
      const updatedSnap = await stationRef.get();
      const updatedStation = updatedSnap.data() as StationDoc | undefined;
      if (updatedStation) {
        await advanceQueue(stationId, updatedStation);
      }
    } catch (error) {
      logger.error(
        `Error expiring reservation for station ${stationId}:`,
        error,
      );
      throw error;
    }
  },
);
