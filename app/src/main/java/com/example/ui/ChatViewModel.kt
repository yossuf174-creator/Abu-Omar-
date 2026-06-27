package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.chatDao())

    val contacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stories: StateFlow<List<Story>> = repository.allStories
        .map { storyList ->
            val now = System.currentTimeMillis()
            storyList.filter { now - it.timestamp < 24L * 60 * 60 * 1000 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun publishStory(text: String, type: String, backgroundType: String = "BLUE_PURPLE") {
        viewModelScope.launch(Dispatchers.IO) {
            val storyId = "user_story_${System.currentTimeMillis()}"
            val newStory = Story(
                id = storyId,
                contactId = "user_me",
                contactName = "أبو عمر (أنا)",
                contactAvatarColor = "#0084FF",
                mediaUrl = if (type == "IMAGE") "image_placeholder" else if (type == "VIDEO") "video_placeholder" else "",
                backgroundBrushType = backgroundType,
                text = text,
                timestamp = System.currentTimeMillis(),
                storyType = type
            )
            repository.insertStory(newStory)
        }
    }

    private val _selectedContactId = MutableStateFlow<String?>(null)
    val selectedContactId: StateFlow<String?> = _selectedContactId.asStateFlow()

    // Fetch messages dynamically when selected contact changes
    val currentMessages: StateFlow<List<Message>> = _selectedContactId
        .flatMapLatest { contactId ->
            if (contactId == null) flowOf(emptyList())
            else repository.getMessagesForContact(contactId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map to keep track of typing indicators for each contact
    private val _typingContacts = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingContacts: StateFlow<Map<String, Boolean>> = _typingContacts.asStateFlow()

    // Map to keep track of unread message count for each contact
    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    // Map to get the latest message for each contact to show in Chats page
    private val _lastMessagesMap = MutableStateFlow<Map<String, Message>>(emptyMap())
    val lastMessagesMap: StateFlow<Map<String, Message>> = _lastMessagesMap.asStateFlow()

    // Settings State
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isActiveStatusEnabled = MutableStateFlow(true)
    val isActiveStatusEnabled: StateFlow<Boolean> = _isActiveStatusEnabled.asStateFlow()

    // Story viewer state
    private val _activeStory = MutableStateFlow<Story?>(null)
    val activeStory: StateFlow<Story?> = _activeStory.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            prepopulateDatabaseIfNeeded()
            // Observe message flow to map last messages and unread counts
            database.chatDao().getAllMessages().collect { allMsgs ->
                val map = allMsgs.groupBy { it.contactId }
                    .mapValues { (_, msgs) -> msgs.first() }
                _lastMessagesMap.value = map

                val unread = allMsgs.filter { !it.isFromMe && it.status != "READ" }
                    .groupBy { it.contactId }
                    .mapValues { (_, msgs) -> msgs.size }
                _unreadCounts.value = unread
            }
        }
    }

    fun selectContact(contactId: String?) {
        _selectedContactId.value = contactId
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleActiveStatus() {
        _isActiveStatusEnabled.value = !_isActiveStatusEnabled.value
    }

    fun viewStory(story: Story?) {
        _activeStory.value = story
    }

    fun replyToStory(story: Story, replyText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedContactId.value = story.contactId
            sendMessage("رداً على قصتك \"${story.text}\": $replyText")
        }
    }

    fun sendThumbsUp() {
        sendMessage("👍")
    }

    fun sendMessage(text: String) {
        val contactId = _selectedContactId.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Save user message locally
            val userMsg = Message(
                contactId = contactId,
                isFromMe = true,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(userMsg)

            // 2. Turn on typing animation
            _typingContacts.update { it + (contactId to true) }

            // 3. Initiate API Call & Retrieve AI Reply
            generateGeminiReply(contactId)
        }
    }

    private suspend fun generateGeminiReply(contactId: String) {
        val contact = repository.getContact(contactId) ?: return
        val recentMessages = currentMessages.value.takeLast(8)

        // Compile prompt structures
        val promptParts = mutableListOf<Part>()
        recentMessages.forEach { msg ->
            val sender = if (msg.isFromMe) "المستخدم" else contact.name
            promptParts.add(Part(text = "$sender: ${msg.text}"))
        }
        promptParts.add(Part(text = "رائعة، تفضّل بالرد الآن (${contact.name}) على الرسالة الأخيرة للمستخدم كرسالة رسائل فورية مسنجر (تجنّب كلياً تكرار كتابة اسمك في أول السطر، أجب كرسالة مباشرة وبلهجتك تماماً وبقصَر وإيجاز)."))

        val systemPrompt = """
            ${contact.systemPrompt}
            قواعد أساسية ومهمة جداً:
            1. أنت مسنجر شات بوت باسم (${contact.name}).
            2. أجب كرسالة فورية سريعة وطبيعية واحدة فقط بلون وبشغف.
            3. لا تستخدم الفصحى الثقيلة إلا إذا كانت شخصيتك تتطلب ذلك (مثل مدير العمل).
            4. أجب باختصار (جملة أو جملتين فقط). لا تقل أبداً "المستخدم:" أو يكرر الاسم.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = promptParts)),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        // Show typing indicator realistic delay
        delay(1600)

        val apiKey = BuildConfig.GEMINI_API_KEY
        var replyText = ""

        try {
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                replyText = getFallbackReply(contactId, recentMessages.lastOrNull()?.text ?: "")
            } else {
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                replyText = responseText?.trim() ?: getFallbackReply(contactId, recentMessages.lastOrNull()?.text ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ChatViewModel", "Gemini API Error", e)
            replyText = getFallbackReply(contactId, recentMessages.lastOrNull()?.text ?: "")
        }

        // Save reply message locally
        val replyMsg = Message(
            contactId = contactId,
            isFromMe = false,
            text = replyText,
            timestamp = System.currentTimeMillis()
        )
        repository.insertMessage(replyMsg)

        // Dismiss typing indicator
        _typingContacts.update { it + (contactId to false) }
    }

    private fun getFallbackReply(contactId: String, lastUserMessage: String): String {
        return when (contactId) {
            "abu_omar" -> "يا هلا بك يا بطل! يسعد أوقاتك بكل خير يا غالي. أنا جاهز دايماً لمساندتك بكودينغ أندرويد وتطوير تطبيق أبو عمر مسنجر ☕💻"
            "um_omar" -> "يا عمر البيلبقله الدلال ربي يحميك ويسلملي وجودك ❤️ السهرة الليلة بانتظارك، لا تنسى تجيب الخبز معك 😘"
            "ahmad_friend" -> "هههههه حلوة منك يا صديقي! الليلة الموعد الثابت في القهوة رتب أمورك ضروري وممنوع الأعذار 🔥📱"
            "manager_work" -> "أهلاً بك، تم استلام رسالتك المتعلقة بالمهام. يرجى المتابعة وتقديم الكود قبل نهاية ساعات العمل اليوم لتفادي التعطيل."
            else -> "تم استلام رسالتك بنجاح! 👍"
        }
    }

    private suspend fun prepopulateDatabaseIfNeeded() {
        val count = repository.allContacts.first().size
        if (count == 0) {
            val initialContacts = listOf(
                Contact(
                    id = "abu_omar",
                    name = "أبو عمر (الخبير التقني)",
                    bio = "مطور أندرويد شغوف ومحب للتوجيه التقني. قهوتي سادة وأكودي نظيفة ☕️💻",
                    initialMessage = "أهج يا بطل! يسعد أوقاتك بكل خير. شو عم نبرمج اليوم في تطبيق أبو عمر؟",
                    systemPrompt = "أنت أبو عمر، مطور برمجيات خبيث وذكي وصديق مخلص. لغتك هي العامية الشامية السلسة الممتعة مع الفصحى. تعشق الكود والتطوير، تقدم نصائح مذهلة وتستخدم تعبيرات تقنية وأيموجيات مثل 💻, ☕️, 🚀, 🛠️.",
                    relation = "مطور وموجه تقني",
                    avatarColorHex = "#0D6EFD"
                ),
                Contact(
                    id = "um_omar",
                    name = "أم عمر (شريكة الحياة)",
                    bio = "البيت والقلب السعيد ❤️ مهتمة بالتفاصيل الدافئة وعائلتي هي دنيتي كلها.",
                    initialMessage = "يعطيك العافية يا حبيبي، كيف الشغل؟ لا تنسى تجيب معك خبز وفواكه وأنت راجع 😘",
                    systemPrompt = "أنتِ أم عمر، زوجة المستخدم أو شريكة الحياة الدافئة والحنونة جداً. تتحدثين بلهجة دمشقية دافئة ملؤها الدلال والمحبة. تسألين دائماً عن صحته، وخدمته وتحرصين على شراء لوازم المنزل، وترسلين قلوب ودعوات دافئة بصدق. استخدمي إيموجيات ❤️, 🥰, 🍲, 🛒.",
                    relation = "العائلة",
                    avatarColorHex = "#E11D48"
                ),
                Contact(
                    id = "ahmad_friend",
                    name = "أحمد مسعد (صديق الطفولة)",
                    bio = "شغوف بكرة القدم والطلعات وحل المشاكل اليومية الكالسة⚽️🎮",
                    initialMessage = "يا جبل ما يهزك ريح! الليلة في مباراة قوية بالمقهى، حجزت المقاعد لو بعدك نايم؟",
                    systemPrompt = "أنت أحمد، صديق الطفولة المقرب جداً والوفي. تتحدث بالهجة الأردنية أو السورية العامية الحماسية والشبابية جداً والممزوجة بالمصطلحات الفكاهية. تحب الرياضة والطلعات وألعاب الڤيديو. إيموجياتك ⚽️, 🔥, 😎, 🎯, 🎮.",
                    relation = "صديق مقرب",
                    avatarColorHex = "#10B981"
                ),
                Contact(
                    id = "manager_work",
                    name = "م. عصام (مدير العمل)",
                    bio = "مدير المشاريع التقنية. الدقة والالتزام بالمواعيد هما سر النجاح 📊💼",
                    initialMessage = "السلام عليكم، هل تم الانتهاء من مراجعة الكود البرمجي الأخير؟ يرجى إرسال التحديث اليوم.",
                    systemPrompt = "أنت المهندس عصام، مدير العمل الرسمي المحترم والصارم في مواعيده. تتكلم باللغة العربية الفصحى المهذبة والمهنية العالية مع لهجة خليجية خفيفة جداً. تحرص على الوقت والاحترافية والمهام. إيموجياتك 📈, 💼, 📁, 📝.",
                    relation = "العمل",
                    avatarColorHex = "#4B5563"
                )
            )

            repository.insertContacts(initialContacts)

            // Seed initial messages to show active history
            initialContacts.forEach { contact ->
                repository.insertMessage(
                    Message(
                        contactId = contact.id,
                        isFromMe = false,
                        text = contact.initialMessage,
                        timestamp = System.currentTimeMillis() - 7200000 // 2 hours ago
                    )
                )
            }

            // Seed initial Stories
            val initialStories = listOf(
                Story(
                    id = "st1",
                    contactId = "abu_omar",
                    contactName = "أبو عمر (الخبير التقني)",
                    contactAvatarColor = "#0D6EFD",
                    mediaUrl = "coding",
                    backgroundBrushType = "BLUE_PURPLE",
                    text = "الكود النظيف ليس كوداً يعمل، بل كوداً يمكن قراءته وتطويره بسهولة! 🚀💻"
                ),
                Story(
                    id = "st2",
                    contactId = "um_omar",
                    contactName = "أم عمر (شريكة الحياة)",
                    contactAvatarColor = "#E11D48",
                    mediaUrl = "baking",
                    backgroundBrushType = "ROSE_ORANGE",
                    text = "أحلى فطور صباحي دافئ لعائلتي الحبيبة ❤️ ربي يسعدلي يومكم ويحميهم!"
                ),
                Story(
                    id = "st3",
                    contactId = "ahmad_friend",
                    contactName = "أحمد مسعد",
                    contactAvatarColor = "#10B981",
                    mediaUrl = "football",
                    backgroundBrushType = "DARK_GREEN",
                    text = "جاهزون للمباراة التاريخية الليلة؟ التوقعات نار والسهرة للجميع! 🔥⚽"
                )
            )

            repository.insertStories(initialStories)
        }
    }
}
