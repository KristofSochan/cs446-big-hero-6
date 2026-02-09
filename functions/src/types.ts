/**
 * Type definitions for Firestore documents
 */

import * as admin from "firebase-admin";

export interface Attendee {
  userId: string;
  status: "waiting" | "attending" | "removed";
  joinedAt: admin.firestore.Timestamp;
}

export interface CurrentSession {
  userId: string | null;
  startedAt: admin.firestore.Timestamp | null;
  expiresAt: admin.firestore.Timestamp | null;
}

export interface Station {
  name: string;
  isActive: boolean;
  sessionDurationSeconds?: number;
  attendees: Attendee[];
  currentSession?: CurrentSession;
}

export interface User {
  deviceId: string;
  fcmToken?: string;
  currentWaitlists?: string[];
}
