package ca.uwaterloo.cs446.bighero6.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class User(
    @DocumentId
    val docId: String = "",
    val id: String = "", // same val as docId but is a standard field of the document
    val fcmToken: String? = null,
    val currentWaitlists: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null  // Set by server on creation
)
