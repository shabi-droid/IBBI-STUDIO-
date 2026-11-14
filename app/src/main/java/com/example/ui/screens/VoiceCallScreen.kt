package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.ui.state.ChatMessagesState
import com.example.ui.state.ChatViewModel
import com.example.voice.VoiceCallManager

@Composable
fun VoiceCallScreen(
    viewModel: ChatViewModel,
    userProfile: UserProfile,
    onSwitchToTextChat: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }

    val context = LocalContext.current
    val persona = viewModel.persona
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var speakerEnabled by remember { mutableStateOf(true) }
    val currentSpeakerEnabled by rememberUpdatedState(speakerEnabled)
    var partialTranscript by remember { mutableStateOf("") }
    var voiceStatusMessage by remember { mutableStateOf("Tap the microphone to talk with ${persona.name}") }
    var showTextSpeakDialog by remember { mutableStateOf(false) }
    var textSpeakDraft by remember { mutableStateOf("") }

    val voiceManager = remember(context, persona.id) {
        var instance: VoiceCallManager? = null
        instance = VoiceCallManager(
            context = context,
            onTranscriptReady = { spoken ->
                partialTranscript = ""
                voiceStatusMessage = "You: \"$spoken\""
                viewModel.sendMessage(
                    rawText = spoken,
                    userProfile = userProfile,
                    isVoiceMode = true,
                    onReplyReadyForVoice = { reply ->
                        if (currentSpeakerEnabled) {
                            instance?.speak(reply)
                        }
                    }
                )
            },
            onPartialTranscript = { partial ->
                partialTranscript = partial
            },
            onListeningChanged = { listening ->
                isListening = listening
                if (listening) {
                    voiceStatusMessage = "Listening..."
                }
            },
            onSpeakingChanged = { speaking ->
                isSpeaking = speaking
            },
            onSpeechError = { err ->
                voiceStatusMessage = err
            }
        )
        instance
    }

    DisposableEffect(voiceManager) {
        onDispose {
            voiceManager.release()
        }
    }

    val sendVoiceTurn: (String) -> Unit = { spoken ->
        voiceStatusMessage = "You: \"$spoken\""
        viewModel.sendMessage(
            rawText = spoken,
            userProfile = userProfile,
            isVoiceMode = true,
            onReplyReadyForVoice = { reply ->
                if (speakerEnabled) {
                    voiceManager.speak(reply)
                }
            }
        )
    }

    // Re-bind manager callback safely via wrapper
    val triggerVoiceInput: () -> Unit = {
        if (isListening) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            triggerVoiceInput()
        } else {
            voiceStatusMessage = "Microphone permission declined. You can still use 'Speak via Text' below."
        }
    }

    val latestModelReply = remember(messagesState, streamingReply) {
        if (streamingReply.isNotBlank()) {
            streamingReply
        } else {
            (messagesState as? ChatMessagesState.Success)
                ?.messages
                ?.lastOrNull { it.sender == "model" }
                ?.text
                ?: "Hi ${userProfile.name.split(" ").firstOrNull() ?: "there"}! I'm on the line — what's on your mind?"
        }
    }

    if (showTextSpeakDialog) {
        AlertDialog(
            onDismissRequest = { showTextSpeakDialog = false },
            title = { Text("Voice Line Input") },
            text = {
                OutlinedTextField(
                    value = textSpeakDraft,
                    onValueChange = { textSpeakDraft = it },
                    label = { Text("Say something to ${persona.name}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_fallback_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val spoken = textSpeakDraft.trim()
                        if (spoken.isNotEmpty()) {
                            textSpeakDraft = ""
                            showTextSpeakDialog = false
                            sendVoiceTurn(spoken)
                        }
                    },
                    modifier = Modifier.testTag("voice_fallback_send")
                ) {
                    Text("Speak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextSpeakDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking || isGenerating) 1.18f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF211834),
                        Color(0xFF33244A),
                        Color(0xFF1A1426)
                    )
                )
            )
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Voice Call · AI Companion",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                TextButton(
                    onClick = onSwitchToTextChat,
                    modifier = Modifier.testTag("switch_to_text_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Text Chat", color = Color.White)
                }
            }

            // Center Avatar & Animated Rings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFFFF8A6B).copy(alpha = 0.16f))
                    )
                    Box(
                        modifier = Modifier
                            .size(164.dp)
                            .scale(pulseScale * 0.96f)
                            .clip(CircleShape)
                            .background(Color(0xFFFF8A6B).copy(alpha = 0.25f))
                    )
                    Image(
                        painter = painterResource(id = persona.avatarRes),
                        contentDescription = persona.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        isListening -> "Listening to you..."
                        isGenerating -> "${persona.name} is thinking..."
                        isSpeaking -> "${persona.name} is speaking..."
                        else -> "${persona.roleLabel} · Connected"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFFB59F)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (partialTranscript.isNotBlank()) "Hearing: \"$partialTranscript\"" else voiceStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = latestModelReply,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Call Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(
                    onClick = { showTextSpeakDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("voice_type_fallback_button")
                ) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Speak via Text", color = Color.White)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            speakerEnabled = !speakerEnabled
                            if (!speakerEnabled) voiceManager.stopSpeaking()
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("voice_speaker_toggle")
                    ) {
                        Icon(
                            imageVector = if (speakerEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = if (speakerEnabled) "Mute voice output" else "Unmute voice output"
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            if (hasAudioPermission) {
                                triggerVoiceInput()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) Color(0xFFFF8A6B) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Tap to speak",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            voiceManager.stopListening()
                            voiceManager.stopSpeaking()
                            onEndCall()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFD9383A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("voice_end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End Call"
                        )
                    }
                }
            }
        }
    }
}
