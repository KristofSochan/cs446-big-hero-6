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

export interface CurrentReservation {
  userId: string;
  expiresAt: admin.firestore.Timestamp;
}

export interface Station {
  name: string;
  isActive: boolean;
  mode?: string;
  sessionDurationSeconds?: number;
  enforceCheckinLimit?: boolean;
  checkinWindowSeconds?: number;
  attendees: Record<string, Attendee>;
  currentSession?: CurrentSession;
  currentReservation?: CurrentReservation;
}

export interface User {
  deviceId: string;
  fcmToken?: string;
  currentWaitlists?: string[];
}
