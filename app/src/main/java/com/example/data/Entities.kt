package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val name: String,
    val bio: String,
    val initialMessage: String,
    val systemPrompt: String,
    val avatarUrl: String = "",
    val isActiveNow: Boolean = true,
    val lastSeenText: String = "نشط الآن",
    val relation: String = "", // e.g. "مطور", "صديق", "زوجة", "عمل"
    val avatarColorHex: String = "#0084FF" // Hex color representation
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String,
    val isFromMe: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "READ" // "SENT", "DELIVERED", "READ"
)

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey val id: String,
    val contactId: String,
    val contactName: String,
    val contactAvatarColor: String,
    val mediaUrl: String, 
    val backgroundBrushType: String = "BLUE_PURPLE", // Gradient indicator
    val text: String = "", 
    val timestamp: Long = System.currentTimeMillis(),
    val storyType: String = "TEXT" // "TEXT", "IMAGE", "VIDEO"
)
