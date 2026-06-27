package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val allContacts: Flow<List<Contact>> = chatDao.getAllContacts()
    val allStories: Flow<List<Story>> = chatDao.getAllStories()

    fun getMessagesForContact(contactId: String): Flow<List<Message>> =
        chatDao.getMessagesForContact(contactId)

    suspend fun getContact(id: String): Contact? = chatDao.getContactById(id)

    suspend fun insertMessage(message: Message): Long = chatDao.insertMessage(message)

    suspend fun insertContact(contact: Contact) = chatDao.insertContact(contact)

    suspend fun insertContacts(contacts: List<Contact>) = chatDao.insertContacts(contacts)

    suspend fun insertStories(stories: List<Story>) = chatDao.insertStories(stories)

    suspend fun insertStory(story: Story) = chatDao.insertStory(story)

    suspend fun clearHistory(contactId: String) = chatDao.clearChatHistory(contactId)
}
