# Data Model

## Overview

TapList uses Firebase Firestore as its database. The data model consists of two main collections: `stations` (which includes waitlist data) and `users`, plus embedded data structures for attendees and sessions.

## Firestore Collections

```
/stations/{stationId}  (includes station config + waitlist data)
/users/{userId}
```

## Data Structures

### Station

**Collection:** `/stations/{stationId}`

Represents a physical waitlist station (e.g., "Pinball Machine #1", "Restaurant Seating") **and its waitlist data**. Station configuration and waitlist queue are stored together in a single document.

```kotlin
data class Station(
    @DocumentId val id: String = "",
    val name: String = "",
    val isActive: Boolean = true,
    val sessionDurationMinutes: Int = 15,
    val mode: String = "manual", // "manual" or "timed"
    // Waitlist data embedded (map keyed by userId for server timestamps)
    val attendees: Map<String, Attendee> = emptyMap(),
    val currentSession: CurrentSession? = null
)
```

**Fields:**

- `id` - Document ID (auto-mapped from Firestore document ID)
- `name` - Display name of the station
- `isActive` - Whether the station is accepting new attendees
- `sessionDurationMinutes` - How long each session lasts (default: 15 minutes)
- `mode` - Waitlist/session behavior: `"manual"` (session ends only when user taps **End Session**) or `"timed"` (session automatically expires after `sessionDurationMinutes`)
- `attendees` - Map of userId → Attendee (queue order is by sorting values by `joinedAt`; join time uses server timestamp)
- `currentSession` - Currently active session (null if no one is using it)

**Helper Methods:**

- `calculatePosition(userId)` - Returns user's position (1-based) by sorting attendees by `joinedAt` timestamp
- `isAtPositionOne(userId)` - Checks if user is at the front of the queue

**Example:**

```json
{
  "name": "Pinball Machine #1",
  "isActive": true,
  "sessionDurationMinutes": 15,
  "mode": "manual",
  "attendees": {
    "user1": {
      "userId": "user1",
      "status": "waiting",
      "joinedAt": "2024-01-01T10:00:00Z"
    },
    "user2": {
      "userId": "user2",
      "status": "waiting",
      "joinedAt": "2024-01-01T10:05:00Z"
    }
  },
  "currentSession": null
}
```

**Design Decision:** Station and Waitlist are combined because:

- Each station has exactly one waitlist (1:1 relationship)
- They're always accessed together
- Simplifies queries (one document read instead of two)
- Enables atomic updates of station config and waitlist state

---

### Attendee

**Embedded in:** `Station.attendees` map (key = userId)

Represents a single person in a waitlist.

```kotlin
data class Attendee(
    val userId: String = "",
    val status: String = "waiting", // "waiting", "attending", "removed"
    val joinedAt: Timestamp = Timestamp.now()
)
```

**Fields:**

- `userId` - Reference to user document
- `status` - Current status: `"waiting"`, `"attending"`, or `"removed"`
- `joinedAt` - When the user joined the queue (server timestamp; used for position calculation)

**Status Values:**

- `"waiting"` - User is in queue, waiting for their turn
- `"attending"` - User is currently using the station (should be removed from attendees map)
- `"removed"` - User was manually removed (optional, can just delete from map)

---

### CurrentSession

**Embedded in:** `Station.currentSession`

Represents the currently active session at a station.

```kotlin
data class CurrentSession(
    val userId: String? = null,
    val startedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
)
```

**Fields:**

- `userId` - User currently using the station (null if no active session)
- `startedAt` - When the session started
- `expiresAt` - When the session expires (startedAt + sessionDurationMinutes)

---

### User

**Collection:** `/users/{userId}`

Represents an attendee user (not operators).

```kotlin
data class User(
    @DocumentId val userId: String = "",
    val deviceId: String = "",
    val fcmToken: String? = null,
    val currentWaitlists: List<String> = emptyList()
)
```

**Fields:**

- `userId` - Document ID (generated from device ID or Android ID)
- `deviceId` - Unique device identifier
- `fcmToken` - Firebase Cloud Messaging token for push notifications (null until registered)
- `currentWaitlists` - Array of station IDs where user is currently waiting

**Example:**

```json
{
  "deviceId": "9774d56d682e549c",
  "fcmToken": "dGhpcyBpcyBhIGZha2UgdG9rZW4...",
  "currentWaitlists": ["abc123", "def456"]
}
```

---

## Relationships

```
Station
  ├──> (many) Attendee ──> (1) User
  └──> (0..1) CurrentSession ──> (1) User
```

- Each **Station** contains many **Attendees** (embedded map keyed by userId)
- Each **Attendee** references one **User**
- Each **Station** has zero or one **CurrentSession** (embedded)
- Each **CurrentSession** references one **User**

## Key Design Decisions

### 1. Position Calculated from Timestamp

**Why:** Avoids complex reordering logic when users leave. Position is always `sortedBy(joinedAt).indexOf(user) + 1`.

**Trade-off:** Requires sorting on every position check, but acceptable for prototype (< 100 users per waitlist).

### 2. Attendees Map vs Subcollection

**Why:** Using a map (keyed by userId) embedded in Station document instead of a subcollection:

- Simpler queries (single document read)
- Atomic transactions easier
- Real-time updates simpler
- Station config and waitlist data together (one read)

**Trade-off:** Document size limit (1MB), but fine for prototype scale.

### 3. No Explicit Position Field

**Why:** Position is derived from `joinedAt` timestamp. Prevents inconsistencies and simplifies code.

**Trade-off:** Slightly more computation, but eliminates position update bugs.

### 4. CurrentSession Separate from Attendees

**Why:** When user starts session, they're removed from `attendees` map and added to `currentSession`. This makes it clear who's actively using vs waiting.

**Benefit:** Easy to query "who's waiting" vs "who's using".

### 5. User Document Per Device

**Why:** Each device gets a unique user document. No account creation needed (privacy requirement).

**Implementation:** Device ID → User ID mapping stored in SharedPreferences.

## Example Data Flow

### User Joins Waitlist

1. User taps NFC tag → `taplist://station/abc123`
2. App fetches `/stations/abc123` → Gets station name and waitlist data
3. User confirms join
4. Transaction:
   - Set `/stations/abc123/attendees/{userId}` = `Attendee(userId, "waiting", serverTimestamp())`
   - Add `"abc123"` to `/users/{userId}/currentWaitlists`
5. Position calculated: `station.calculatePosition(userId)` → Returns 4

### User Becomes Position 1

1. User at position 1 taps NFC tag again
2. Check: `station.isAtPositionOne(userId)` → true
3. Transaction:
   - Set `/stations/abc123/currentSession` = `CurrentSession(userId, now, now+15min)`
   - Delete `/stations/abc123/attendees/{userId}`
4. Navigate to SessionActiveScreen

### Session Expires

1. Client-side timer reaches `expiresAt`
2. Update `/stations/abc123/currentSession` = `null`
3. Next person in `attendees` (by sorted joinedAt) can now start session

## Firestore Security Rules

Basic rules (update for production):

```javascript
/stations/{stationId}
  - read: anyone
  - write: authenticated operators only

/stations/{stationId}
  - read: anyone
  - write: authenticated users (for their own entries) + operators

/users/{userId}
  - read: user themselves
  - write: user themselves
```
