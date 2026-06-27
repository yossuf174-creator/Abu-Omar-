package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Contact
import com.example.data.Message
import com.example.data.Story
import com.example.ui.theme.ActiveStatusGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.MessengerBlue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessengerAppContent(viewModel: ChatViewModel) {
    val selectedContactId by viewModel.selectedContactId.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    var showProfileSettings by remember { mutableStateOf(false) }
    var showCreateStoryScreen by remember { mutableStateOf(false) }

    val screen = when {
        selectedContactId != null -> "CHAT"
        showCreateStoryScreen -> "STORY"
        showProfileSettings -> "PROFILE"
        else -> "LIST"
    }

    MyApplicationTheme(darkTheme = isDarkMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)))
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    "CHAT" -> {
                        ChatConversationScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.selectContact(null) }
                        )
                    }
                    "STORY" -> {
                        CreateStoryScreen(
                            viewModel = viewModel,
                            onBack = { showCreateStoryScreen = false }
                        )
                    }
                    "PROFILE" -> {
                        ProfileSettingsScreen(
                            viewModel = viewModel,
                            onBack = { showProfileSettings = false }
                        )
                    }
                    else -> {
                        ChatsListScreen(
                            viewModel = viewModel,
                            onContactClick = { viewModel.selectContact(it.id) },
                            onProfileClick = { showProfileSettings = true },
                            onCreateStoryClick = { showCreateStoryScreen = true }
                        )
                    }
                }
            }

            // Global Full Screen Story Viewer
            val activeStory by viewModel.activeStory.collectAsState()
            activeStory?.let { story ->
                StoryViewerScreen(
                    story = story,
                    onDismiss = { viewModel.viewStory(null) },
                    onReply = { reply ->
                        viewModel.replyToStory(story, reply)
                        viewModel.viewStory(null)
                    }
                )
            }
        }
    }
}

// ------------------------------------------------------------------------
// SCREEN 1: CHATS LIST SCREEN (Facebook Messenger Main Interface)
// ------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: ChatViewModel,
    onContactClick: (Contact) -> Unit,
    onProfileClick: () -> Unit,
    onCreateStoryClick: () -> Unit
) {
    val contacts by viewModel.contacts.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val lastMessages by viewModel.lastMessagesMap.collectAsState()
    val isActiveStatusEnabled by viewModel.isActiveStatusEnabled.collectAsState()
    val typingContacts by viewModel.typingContacts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.trim().isEmpty()) contacts
        else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الدَردشات",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                navigationIcon = {
                    // Profile picture button inside header
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MessengerBlue.copy(alpha = 0.2f))
                            .clickable { onProfileClick() }
                            .testTag("my_profile_avatar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "أع", // "أبو عمر" initials
                            color = MessengerBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCreateStoryClick,
                        modifier = Modifier.testTag("top_camera_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "عرض إضافة قصة", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Sleek Search Bar (Facebook style)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("chat_search_ip"),
                placeholder = { Text("بحث", textAlign = TextAlign.Right, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // 2. Stories Bar (Horizontal Feed)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // First Item: User's Story (قصتك)
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onCreateStoryClick() }
                                ) {
                                    Box(
                                        modifier = Modifier.size(62.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "أنا",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.BottomEnd)
                                                .clip(CircleShape)
                                                .background(MessengerBlue)
                                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "أضف قصة",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "قصتك",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Abu Omar Stories
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { 
                                        viewModel.viewStory(
                                            com.example.data.Story(
                                                id = "abu_omar_story_1",
                                                contactId = "abu_omar",
                                                contactName = "أبو عمر",
                                                contactAvatarColor = "#4CAF50",
                                                mediaUrl = "",
                                                backgroundBrushType = "DARK_GREEN",
                                                text = "مرحباً بكم في قصص أبو عمر! أشارككم هنا يومياتي وتحديثاتي السريعة.",
                                                storyType = "TEXT"
                                            )
                                        )
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, MessengerBlue, CircleShape)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_abu_omar_story),
                                            contentDescription = "قصص أبو عمر",
                                            modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "أبو عمر",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            // Dynamic Active Stories List
                            items(stories) { story ->
                                val contact = contacts.find { it.id == story.contactId }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { viewModel.viewStory(story) }
                                        .testTag("story_${story.id}")
                                ) {
                                    AvatarCircle(
                                        name = story.contactName,
                                        colorHex = story.contactAvatarColor,
                                        sizeDp = 56.dp,
                                        isActive = isActiveStatusEnabled && (contact?.isActiveNow ?: false),
                                        storyBorder = true
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = story.contactName.substringBefore(" "),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Conversations Feed (Chats Scroll View)
                if (filteredContacts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "لا يوجد محادثات",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد نتائج مطابقة لبحثك",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(filteredContacts) { contact ->
                        val lastMsg = lastMessages[contact.id]
                        val isTyping = typingContacts[contact.id] ?: false

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onContactClick(contact) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("contact_row_${contact.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarCircle(
                                name = contact.name,
                                colorHex = contact.avatarColorHex,
                                sizeDp = 58.dp,
                                isActive = isActiveStatusEnabled && contact.isActiveNow
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = contact.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (contact.isActiveNow) ActiveStatusGreen else Color.Red)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        val timeString = lastMsg?.timestamp?.let { formatTime(it) } ?: ""
                                        Text(
                                            text = timeString,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                        
                                        val unreadCount = viewModel.unreadCounts.collectAsState().value[contact.id] ?: 0
                                        if (unreadCount > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isTyping) {
                                        Text(
                                            text = "يكتب الآن...",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MessengerBlue
                                        )
                                    } else {
                                        Text(
                                            text = if (lastMsg?.isFromMe == true) "أنت: ${lastMsg.text}" else lastMsg?.text ?: contact.bio,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (lastMsg?.isFromMe == false && lastMsg.status != "READ") {
                                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                                            } else {
                                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                            },
                                            fontWeight = if (lastMsg?.isFromMe == false && lastMsg.status != "READ") {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// SCREEN 2: CHAT CONVERSATION SCREEN
// ------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val contactId = viewModel.selectedContactId.collectAsState().value ?: return
    val contacts by viewModel.contacts.collectAsState()
    val contact = contacts.find { it.id == contactId } ?: return
    val messages by viewModel.currentMessages.collectAsState()
    val isTyping = viewModel.typingContacts.collectAsState().value[contact.id] ?: false
    val isActiveStatusEnabled by viewModel.isActiveStatusEnabled.collectAsState()

    var textState by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* Could show bios */ }
                    ) {
                        AvatarCircle(
                            name = contact.name,
                            colorHex = contact.avatarColorHex,
                            sizeDp = 40.dp,
                            isActive = isActiveStatusEnabled && contact.isActiveNow
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (contact.isActiveNow) ActiveStatusGreen else Color.Red)
                                )
                            }
                            Text(
                                text = if (isActiveStatusEnabled && contact.isActiveNow) "نشط الآن" else "غير متصل",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        // Standard Arrow back
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "اتصال صوتي", tint = MessengerBlue)
                    }
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "إعدادات المحادثة", tint = MessengerBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                reverseLayout = false
            ) {
                // Info block at the top
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarCircle(
                            name = contact.name,
                            colorHex = contact.avatarColorHex,
                            sizeDp = 86.dp,
                            isActive = isActiveStatusEnabled && contact.isActiveNow
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.relation,
                            fontSize = 13.sp,
                            color = MessengerBlue,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = contact.bio,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 30.dp)
                        )
                    }
                }

                // Chat bubble list
                items(messages) { message ->
                    val isFirstMessageGroup = remember { true } // Simplify clustered UI details

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
                    ) {
                        if (!message.isFromMe) {
                            AvatarCircle(
                                name = contact.name,
                                colorHex = contact.avatarColorHex,
                                sizeDp = 28.dp,
                                isActive = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Message text bubble
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (message.isFromMe) 18.dp else 4.dp,
                                        bottomEnd = if (message.isFromMe) 4.dp else 18.dp
                                    )
                                )
                                .background(
                                    if (message.isFromMe) {
                                        Brush.linearGradient(listOf(MessengerBlue, MessengerBlue.copy(alpha = 0.85f)))
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.secondary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                .widthIn(max = 260.dp)
                                .testTag("message_bubble")
                        ) {
                            Text(
                                text = message.text,
                                fontSize = 15.sp,
                                color = if (message.isFromMe) Color.White else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // Typing bubble animation (Facebook style bouncing dots placeholder)
                if (isTyping) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            AvatarCircle(
                                name = contact.name,
                                colorHex = contact.avatarColorHex,
                                sizeDp = 28.dp,
                                isActive = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BouncingDotsTypingIndicator()
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left accessories: Camera, Photo, Mic icons
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "ملحقات", tint = MessengerBlue)
                    }

                    // Input Field (Aa style)
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("chat_input_field"),
                        placeholder = { Text("اكتب رسالة...") },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textState.trim().isNotEmpty()) {
                                    viewModel.sendMessage(textState)
                                    textState = ""
                                }
                            }
                        )
                    )

                    // Right action: Send or iconic Thumbs up
                    if (textState.trim().isEmpty()) {
                        IconButton(
                            onClick = { viewModel.sendThumbsUp() },
                            modifier = Modifier.testTag("thumbs_up_button")
                        ) {
                            Text("👍", fontSize = 24.sp)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(textState)
                                textState = ""
                            },
                            modifier = Modifier.testTag("send_msg_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "إرسال", tint = MessengerBlue)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// SCREEN 3: PROFILE SETTINGS SCREEN (Me settings)
// ------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isActiveStatusEnabled by viewModel.isActiveStatusEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الملف الشخصي", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Profile display card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(MessengerBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("أنا", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("أبو عمر البطل", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("مستخدم تطبيق أبو عمر مسنجر الذكي", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            // Settings toggles
            ListItem(
                headlineContent = { Text("الوضع الداكن", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("تشغيل السمة الداكنة لتوفير شحن البطارية وراحة العين") },
                leadingContent = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.DarkGray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, contentDescription = "سمة", tint = Color.Gray) } },
                trailingContent = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            )

            ListItem(
                headlineContent = { Text("حالة النشاط", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("عرض عندما تكون نشطاً أو قمت بالنشاط مؤخراً") },
                leadingContent = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ActiveStatusGreen.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = "نشاط", tint = ActiveStatusGreen) } },
                trailingContent = {
                    Switch(
                        checked = isActiveStatusEnabled,
                        onCheckedChange = { viewModel.toggleActiveStatus() },
                        modifier = Modifier.testTag("active_status_switch")
                    )
                }
            )

            ListItem(
                headlineContent = { Text("قفل التطبيق / الأمان", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("طلب بصمة الوجه أو الإصبع لفتح تطبيق أبو عمر") },
                leadingContent = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MessengerBlue.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, contentDescription = "قفل", tint = MessengerBlue) } },
                trailingContent = {
                    Switch(checked = false, onCheckedChange = {})
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Footer branding
            Text(
                text = "تطبيق أبو عمر مسنجر ذ.م.م\nالإصدار 1.0.0 (مدعوم بـ Gemini AI)",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

// ------------------------------------------------------------------------
// SCREEN 4: FULL SCREEN STORIES VIEW INTERACTABLE SCREEN
// ------------------------------------------------------------------------
@Composable
fun StoryViewerScreen(
    story: Story,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }

    val brush = remember(story.backgroundBrushType) {
        when (story.backgroundBrushType) {
            "BLUE_PURPLE" -> Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)))
            "ROSE_ORANGE" -> Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)))
            "DARK_GREEN" -> Brush.linearGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d)))
            else -> Brush.linearGradient(listOf(MessengerBlue, Color(0xFF00C6FF)))
        }
    }

    // Timer effect to close story after 6 seconds
    LaunchedEffect(story) {
        delay(6000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("full_story_viewer")
    ) {
        // Story Background (Dynamic Gradient!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            when (story.storyType) {
                "IMAGE" -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(0.85f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (story.mediaUrl.contains("coding") || story.text.contains("برمج") || story.text.contains("code")) Icons.Default.Build 
                                                  else if (story.mediaUrl.contains("bake") || story.text.contains("روق") || story.text.contains("قهو")) Icons.Default.Home 
                                                  else if (story.mediaUrl.contains("football") || story.text.contains("كور") || story.text.contains("لعب")) Icons.Default.PlayArrow 
                                                  else Icons.Default.Star,
                                    contentDescription = "صورة القصة",
                                    tint = Color.White,
                                    modifier = Modifier.size(100.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "📸 الصورة المرفقة",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = story.text,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                "VIDEO" -> {
                    var elapsedSec by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (elapsedSec < 5) {
                            delay(1000)
                            elapsedSec += 1
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(0.85f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(2.dp, MessengerBlue, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "فيديو نشط",
                                    tint = MessengerBlue,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "🎥 تشغيل فيديو قصير (0:0${elapsedSec} / 0:05)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { elapsedSec / 5f },
                                    modifier = Modifier.width(180.dp).height(4.dp),
                                    color = MessengerBlue,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = story.text.ifEmpty { "🎥 فيديو قصير لمسة من الحركة" },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    Text(
                        text = story.text,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }

        // Top Layer: Progress timers & Profile Details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            // Horizontal Timer Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // A single full animated indicator for prototype visual simplicity
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // User Info header of the story
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = story.contactName.take(2),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = story.contactName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "منذ قليل",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_story_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }

        // Bottom Layer: Reply instantly loop
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .keyboardPadding()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("story_reply_input"),
                placeholder = { Text("يرسل رداً...", color = Color.White.copy(alpha = 0.7f)) },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (replyText.trim().isNotEmpty()) {
                            onReply(replyText)
                            replyText = ""
                        }
                    }
                )
            )

            if (replyText.trim().isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onReply(replyText)
                        replyText = ""
                    },
                    modifier = Modifier.testTag("story_send_reply_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "إرسال رد", tint = MessengerBlue)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// REUSABLE HELPER COMPOSABLES & LOGIC
// ------------------------------------------------------------------------

@Composable
fun AvatarCircle(
    name: String,
    colorHex: String,
    sizeDp: Dp = 50.dp,
    isActive: Boolean = false,
    storyBorder: Boolean = false
) {
    val parsedColor = remember(colorHex) {
        try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            MessengerBlue
        }
    }

    val initials = remember(name) {
        val parts = name.trim().split(" ")
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}"
        } else {
            name.take(2)
        }
    }

    Box(
        modifier = Modifier.size(sizeDp + if (storyBorder) 6.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .then(
                    if (storyBorder) {
                        Modifier.border(
                            2.5.dp,
                            Brush.linearGradient(listOf(Color(0xFF0084FF), Color(0xFFE11D48))),
                            CircleShape
                        )
                    } else Modifier
                )
                .background(parsedColor.copy(alpha = 0.15f))
                .border(1.dp, parsedColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (name.contains("أبو عمر") || name.contains("Abu Omar")) {
                Image(
                    painter = painterResource(id = R.drawable.ic_abu_omar_avatar),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initials,
                    color = parsedColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (sizeDp.value * 0.35f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Active indicator on bottom corner
        if (isActive) {
            Box(
                modifier = Modifier
                    .size((sizeDp.value * 0.28f).coerceIn(12f, 22f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(ActiveStatusGreen)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

// Bouncing bubble dots while AI is thinking
@Composable
fun BouncingDotsTypingIndicator() {
    var state by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            state = (state + 1) % 4
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("typing_bouncing_dots")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(42.dp)
        ) {
            repeat(3) { index ->
                val alpha = if (state == index || state == 3) 1f else 0.35f
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = alpha))
                )
            }
        }
    }
}

// Format time utility helper
fun formatTime(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("h:mm a", Locale("ar"))
    return formatter.format(date)
}

// Simple modifier to handle keyboard safety in fullscreen overlay
@Composable
fun Modifier.keyboardPadding(): Modifier {
    return this.windowInsetsPadding(WindowInsets.ime)
}

// ------------------------------------------------------------------------
// SCREEN 5: CREATE STORY / STATUS SCREEN FOR USERS (TEXT, IMAGE, VIDEO)
// ------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("✍️ نصية", "📸 مصورة", "🎥 فيديو")

    // Text Story state
    var textStatus by remember { mutableStateOf("") }
    var selectedGradient by remember { mutableStateOf("BLUE_PURPLE") }

    // Image Story state
    var imageCaption by remember { mutableStateOf("") }
    var selectedPhotoPreset by remember { mutableStateOf(0) }
    val photoPresets = listOf(
        Pair("☕💻 قهوة وبرمجة", "preset_coding"),
        Pair("🥐☕ صباح الهدوء", "preset_bake"),
        Pair("⚽☀️ مباراة قوية", "preset_football"),
        Pair("✈️🌍 انطلاق وسفر", "preset_travel")
    )

    // Video Story state
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var isRecording by remember { mutableStateOf(false) }
    var recordingTimeLeft by remember { mutableStateOf(5) }
    var isVideoReady by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTimeLeft = 5
            while (recordingTimeLeft > 0) {
                delay(1000)
                recordingTimeLeft -= 1
            }
            isRecording = false
            isVideoReady = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إنشاء حالة جديدة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                        selectedContentColor = MessengerBlue,
                        unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TEXT STATUS CREATOR
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "اختر نص حالتك وخلفيتك المفضلة:",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            // Preview Card
                            val previewBrush = when (selectedGradient) {
                                "BLUE_PURPLE" -> Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)))
                                "ROSE_ORANGE" -> Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)))
                                "DARK_GREEN" -> Brush.linearGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d)))
                                else -> Brush.linearGradient(listOf(MessengerBlue, Color(0xFF00C6FF)))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(previewBrush)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = textStatus.ifEmpty { "أكتب حالتك الرائعة هنا..." },
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Text input field
                            OutlinedTextField(
                                value = textStatus,
                                onValueChange = { textStatus = it },
                                placeholder = { Text("بماذا تفكر الآن؟") },
                                modifier = Modifier.fillMaxWidth().testTag("story_text_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MessengerBlue,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            // Gradient Picker row
                            Text("اختر الخلفية التعبيرية الملونة:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val gradients = listOf(
                                    Pair("موف متوهج", "BLUE_PURPLE"),
                                    Pair("غروب دافئ", "ROSE_ORANGE"),
                                    Pair("حديقة خضراء", "DARK_GREEN"),
                                    Pair("محيط أزرق", "SEA_BLUE")
                                )
                                gradients.forEach { (name, type) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when (type) {
                                                    "BLUE_PURPLE" -> Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)))
                                                    "ROSE_ORANGE" -> Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)))
                                                    "DARK_GREEN" -> Brush.linearGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d)))
                                                    else -> Brush.linearGradient(listOf(MessengerBlue, Color(0xFF00C6FF)))
                                                }
                                            )
                                            .clickable { selectedGradient = type }
                                            .padding(vertical = 12.dp)
                                            .border(
                                                width = if (selectedGradient == type) 2.dp else 0.dp,
                                                color = if (selectedGradient == type) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    if (textStatus.trim().isNotEmpty()) {
                                        viewModel.publishStory(
                                            text = textStatus,
                                            type = "TEXT",
                                            backgroundType = selectedGradient
                                        )
                                        textStatus = ""
                                        onBack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("publish_text_story_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                                shape = RoundedCornerShape(25.dp),
                                enabled = textStatus.trim().isNotEmpty()
                            ) {
                                Text("انشر الحالة الآن 🚀", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    1 -> {
                        // IMAGE STATUS CREATOR
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "اختر مشهد أو صورة تعبّر عن حالتك:",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            // Selected preset indicator canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = when (selectedPhotoPreset) {
                                            0 -> Icons.Default.Build
                                            1 -> Icons.Default.Home
                                            2 -> Icons.Default.PlayArrow
                                            else -> Icons.Default.Add
                                        },
                                        contentDescription = "PRESET ICON",
                                        tint = MessengerBlue,
                                        modifier = Modifier.size(60.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        photoPresets[selectedPhotoPreset].first,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        "محاكاة ممتازة للتحميل والاستوديو",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // Preset Selector Row
                            Text("اختر المشهد المرفق للقصة:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                photoPresets.forEachIndexed { idx, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selectedPhotoPreset == idx) MessengerBlue.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .clickable { selectedPhotoPreset = idx }
                                            .border(
                                                width = if (selectedPhotoPreset == idx) 1.dp else 0.dp,
                                                color = if (selectedPhotoPreset == idx) MessengerBlue else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            pair.first,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (selectedPhotoPreset == idx) {
                                            Icon(Icons.Default.Check, contentDescription = "محدد", tint = MessengerBlue)
                                        }
                                    }
                                }
                            }

                            // Caption field
                            OutlinedTextField(
                                value = imageCaption,
                                onValueChange = { imageCaption = it },
                                placeholder = { Text("أكتب تعليقاً على هذا المشهد الممتاز...") },
                                modifier = Modifier.fillMaxWidth().testTag("story_image_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MessengerBlue,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    viewModel.publishStory(
                                        text = imageCaption.ifEmpty { "منظر جميل لمشاركتكم اليوم!" },
                                        type = "IMAGE",
                                        backgroundType = "SOLAR_SUNSET"
                                    )
                                    imageCaption = ""
                                    onBack()
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("publish_image_story_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                                shape = RoundedCornerShape(25.dp)
                            ) {
                                Text("نشر الصورة والقصة 📸", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    2 -> {
                        // DETAILED CAMERA VIDEO CREATOR
                        if (!hasCameraPermission) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "قفل الكاميرا", tint = Color.Red, modifier = Modifier.size(40.dp))
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    "صلاحية الكاميرا مطلوبة 🎥",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "لكي تتمكن من التقاط وتسجيل مقاطع الفيديو القصيرة الحية وحفظها في حالتك، يرجى تفعيل وصول الكاميرا تالياً.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("تفعيل الصلاحية الآن ✔️", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Immersive camera recorder UI
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "مسجّل فيديو الحالات القصير (5 ثوانٍ):",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Simulated high-fidelity preview
                                    if (isRecording) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "● جاري التسجيل الحيّ من الكاميرا...",
                                                color = Color.Red,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                "0:0$recordingTimeLeft",
                                                color = Color.White,
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            LinearProgressIndicator(
                                                progress = { (5 - recordingTimeLeft) / 5f },
                                                modifier = Modifier.width(150.dp)
                                            )
                                        }
                                    } else if (isVideoReady) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "تم التقاط الفيديو",
                                                tint = ActiveStatusGreen,
                                                modifier = Modifier.size(54.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                "تم تسجيل مقطع فيديو قصير بنجاح!",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                "مدته (5 ثوانٍ) - جاهز للنشر",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "الكاميرا نشطة",
                                                tint = Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                "جهاز الكاميرا نشط ومعاير تماماً",
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "انقر على الزر بالأسفل لبدء التسجيل تالياً",
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Recording trigger
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isRecording) Color.Red.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable(enabled = !isRecording) {
                                            isRecording = true
                                            isVideoReady = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(if (isRecording) Color.Red else Color.Gray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isRecording) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(Color.White)
                                            )
                                        } else {
                                            Icon(Icons.Default.Add, contentDescription = "ابدأ التسجيل", tint = Color.White)
                                        }
                                    }
                                }

                                if (isRecording) {
                                    Text("يرجى عدم التحرك أو غلق التطبيق...", fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("البدء بالتسجيل الحي", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = {
                                        if (isVideoReady) {
                                            viewModel.publishStory(
                                                text = "🎥 تسجيل دافئ من الكاميرا اليوم",
                                                type = "VIDEO",
                                                backgroundType = "DARK_GREEN"
                                            )
                                            isVideoReady = false
                                            onBack()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("publish_video_story_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                                    shape = RoundedCornerShape(25.dp),
                                    enabled = isVideoReady
                                ) {
                                    Text("نشر مقطع الفيديو القصير 🎥", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
