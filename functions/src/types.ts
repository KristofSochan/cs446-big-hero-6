/**
 * Type definitions for Firestore documents
 */

import * as admin from "firebase-admin";

export interface Attendee {
  userId: string;
  status: "waiting" | "attending" | "removed";
  joinedAt: admin.firestore.Timestamp;
  form?: Record<string, string>;
}

export interface CurrentSession {
  userId: string | null;
  startedAt: admin.firestore.Timestamp | null;
  expiresAt: admin.firestore.Timestamp | null;
  sessionId?: string;
}

export interface JoinFormField {
  key: string;
  label: string;
  required?: boolean;
}

export interface CurrentReservation {
  userId: string;
  /** Present when enforceCheckinLimit is on; omitted for notify-only state. */
  expiresAt?: admin.firestore.Timestamp;
  reservationId?: string;
}

export type NotificationMode = "auto" | "manual";

export interface Station {
  ownerId: string;
  name: string;
  isActive: boolean;
  mode?: string;
  sessionDurationSeconds?: number;
  autoJoinEnabled?: boolean;
  operatorManagesSessionsOnly?: boolean;
  notificationMode?: NotificationMode;
  showPositionToGuests?: boolean;
  allowMultipleWaitlists: boolean;
  joinFormFields?: JoinFormField[];
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
