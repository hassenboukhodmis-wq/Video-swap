package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import com.example.data.AiEngine.ArtStyle
import com.example.data.AiEngine.VideoTheme
import com.example.ui.theme.*
import com.example.utils.AudioSynth
import com.example.utils.PresetTemplate
import com.example.viewmodel.CreativeHubViewModel
import com.example.viewmodel.HubUiState
import com.example.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = CreationRepository(database.creationDao())
        val viewModel = ViewModelProvider(
            this,
            ViewModelFactory(repository)
        )[CreativeHubViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainHubScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainHubScreen(viewModel: CreativeHubViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val generalNotification by viewModel.generalNotification.collectAsStateWithLifecycle()

    LaunchedEffect(generalNotification) {
        generalNotification?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotification()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomHubNavigationBar(
                activeTab = currentTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CosmicBackground,
                            CosmicBackground,
                            Color(0xFF0F1122)
                        )
                    )
                )
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState.ordinal > initialState.ordinal) width else -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> if (targetState.ordinal > initialState.ordinal) -width else width } + fadeOut()
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    CreativeHubViewModel.Tab.IMAGE_CREATOR -> ImageCreatorWorkspace(viewModel = viewModel)
                    CreativeHubViewModel.Tab.VIDEO_LAB -> VideoLabWorkspace(viewModel = viewModel)
                    CreativeHubViewModel.Tab.FACE_SWAP -> FaceSwapStudioWorkspace(viewModel = viewModel)
                    CreativeHubViewModel.Tab.GALLERY -> CreationGalleryWorkspace(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun BottomHubNavigationBar(
    activeTab: CreativeHubViewModel.Tab,
    onTabSelected: (CreativeHubViewModel.Tab) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar")
            .height(72.dp),
        containerColor = CosmicSurface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == CreativeHubViewModel.Tab.IMAGE_CREATOR,
            onClick = { onTabSelected(CreativeHubViewModel.Tab.IMAGE_CREATOR) },
            icon = { Icon(Icons.Default.Brush, contentDescription = "AI Image Creator") },
            label = { Text("Image", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CosmicPrimary,
                selectedTextColor = CosmicPrimary,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CosmicSurfaceVariant
            ),
            modifier = Modifier.testTag("image_creator_tab")
        )

        NavigationBarItem(
            selected = activeTab == CreativeHubViewModel.Tab.VIDEO_LAB,
            onClick = { onTabSelected(CreativeHubViewModel.Tab.VIDEO_LAB) },
            icon = { Icon(Icons.Default.Movie, contentDescription = "AI Video Lab") },
            label = { Text("Video", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CosmicPrimary,
                selectedTextColor = CosmicPrimary,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CosmicSurfaceVariant
            ),
            modifier = Modifier.testTag("video_lab_tab")
        )

        NavigationBarItem(
            selected = activeTab == CreativeHubViewModel.Tab.FACE_SWAP,
            onClick = { onTabSelected(CreativeHubViewModel.Tab.FACE_SWAP) },
            icon = { Icon(Icons.Default.Face, contentDescription = "Face Swap Studio") },
            label = { Text("Face Swap", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CosmicPrimary,
                selectedTextColor = CosmicPrimary,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CosmicSurfaceVariant
            ),
            modifier = Modifier.testTag("face_swap_tab")
        )

        NavigationBarItem(
            selected = activeTab == CreativeHubViewModel.Tab.GALLERY,
            onClick = { onTabSelected(CreativeHubViewModel.Tab.GALLERY) },
            icon = { Icon(Icons.Default.History, contentDescription = "Creation Gallery") },
            label = { Text("Gallery", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CosmicPrimary,
                selectedTextColor = CosmicPrimary,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = CosmicSurfaceVariant
            ),
            modifier = Modifier.testTag("gallery_tab")
        )
    }
}

@Composable
fun CustomAStyleChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (isSelected) CosmicPrimary else CosmicSurfaceVariant,
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (isSelected) CosmicPrimary else CardBorder,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ----------------------------------------------------
// WORKSPACE 1: AI IMAGE CREATOR
// ----------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageCreatorWorkspace(viewModel: CreativeHubViewModel) {
    val context = LocalContext.current
    val prompt by viewModel.imagePrompt.collectAsStateWithLifecycle()
    val artStyle by viewModel.imageArtStyle.collectAsStateWithLifecycle()
    val expandedPrompt by viewModel.expandedImagePrompt.collectAsStateWithLifecycle()
    val generatedUrl by viewModel.generatedImageUrl.collectAsStateWithLifecycle()
    val uiState by viewModel.imageUiState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "AI Image Creator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CosmicPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Synthesize descriptive high-fidelity artwork instantly",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { viewModel.imagePrompt.value = it },
            label = { Text("What do you want to create?", color = MutedText) },
            placeholder = { Text("e.g. A futuristic glass helmet explorer on Mars", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_prompt_input")
                .background(CosmicSurface, RoundedCornerShape(12.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = CosmicPrimary,
                unfocusedBorderColor = CardBorder,
                cursorColor = CosmicPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choose Art Style",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArtStyle.values().forEach { style ->
                CustomAStyleChip(
                    text = style.displayName,
                    isSelected = artStyle == style,
                    onClick = { viewModel.imageArtStyle.value = style }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.generateImage() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("generate_image_button")
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CosmicPrimary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState is HubUiState.Loading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Sparkle")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate AI Masterpiece", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = generatedUrl.isNotEmpty() || uiState is HubUiState.Loading,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState is HubUiState.Loading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = CosmicPrimary, strokeWidth = 4.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Gemini directing style details...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CosmicPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Synthesizing pixels without restrictions.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (generatedUrl.isNotEmpty()) {
                            AsyncImage(
                                model = generatedUrl,
                                contentDescription = "Generated AI Image Result",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("generated_image_vnode"),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (expandedPrompt.isNotEmpty() && uiState !is HubUiState.Loading) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Gemini Enhanced Prompt:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CosmicPrimary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = expandedPrompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = White
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.saveGeneratedImageToHistory(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_image_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CosmicSurfaceVariant,
                                        contentColor = White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save Cover")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Keep Image", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.faceSwapUiState.value = HubUiState.Loading
                                        scope.launch {
                                            val path = com.example.utils.ImageUtils.downloadAndSaveImage(context, generatedUrl, "temp_gen_face.jpg")
                                            if (path.isNotEmpty()) {
                                                val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                                                if (bitmap != null) {
                                                    viewModel.targetBaseBitmap.value = bitmap
                                                    viewModel.selectedPreset.value = null
                                                    viewModel.customTargetUri.value = null
                                                    viewModel.currentTab.value = CreativeHubViewModel.Tab.FACE_SWAP
                                                    viewModel.faceSwapUiState.value = HubUiState.Success("Loaded background!")
                                                    viewModel.generalNotification.value = "Loaded creation inside Face Swap Studio!"
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("send_to_faceswap_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CosmicSecondary,
                                        contentColor = White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Face, contentDescription = "Face")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Swap Face", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// WORKSPACE 2: AI VIDEO LAB
// ----------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoLabWorkspace(viewModel: CreativeHubViewModel) {
    val context = LocalContext.current
    val prompt by viewModel.videoPrompt.collectAsStateWithLifecycle()
    val theme by viewModel.videoTheme.collectAsStateWithLifecycle()
    val uiState by viewModel.videoUiState.collectAsStateWithLifecycle()
    
    val scenes by viewModel.storyboardScenes.collectAsStateWithLifecycle()
    val keyframes by viewModel.videoKeyframeUrls.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayingVideo.collectAsStateWithLifecycle()
    val activeFrameIndex by viewModel.currentFrameIndex.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(4000)
                viewModel.advanceVideoTick()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "AI Video Lab",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CosmicPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Express storylines as high-fidelity cinematic screenplays",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { viewModel.videoPrompt.value = it },
            label = { Text("What film story do you want to compose?", color = MutedText) },
            placeholder = { Text("e.g. A cyberpunk motorcycle chase in neon streets", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_prompt_input")
                .background(CosmicSurface, RoundedCornerShape(12.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = CosmicPrimary,
                unfocusedBorderColor = CardBorder,
                cursorColor = CosmicPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select Cinematic Atmosphere",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoTheme.values().forEach { vTheme ->
                CustomAStyleChip(
                    text = vTheme.displayName,
                    isSelected = theme == vTheme,
                    onClick = { viewModel.videoTheme.value = vTheme }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.compileVideo() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compile_video_button")
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CosmicPrimary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState is HubUiState.Loading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MovieFilter, contentDescription = "Film")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compile AI Scripted Video", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = keyframes.isNotEmpty() || uiState is HubUiState.Loading,
            enter = fadeIn() + expandVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState is HubUiState.Loading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = CosmicPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Gemini storyboarder drafting scenes...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CosmicPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Unrestricted chronological AI compiling.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (keyframes.isNotEmpty() && scenes.isNotEmpty()) {
                            val currentScene = scenes.getOrNull(activeFrameIndex)
                            val imageUrl = keyframes[currentScene?.sceneNumber ?: 1] ?: ""

                            val infiniteTransition = rememberInfiniteTransition(label = "KenBurns")
                            val scaleAndPan by infiniteTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "KenBurnsEffect"
                            )

                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Active Movie Keyframe",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (isPlaying) {
                                                scaleX = scaleAndPan
                                                scaleY = scaleAndPan
                                                translationX = (scaleAndPan - 1f) * 20f
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )

                                currentScene?.let { scene ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = scene.subtitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CosmicPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Scene ${scene.sceneNumber} of 4 • Expanded Story Canvas",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (keyframes.isNotEmpty() && scenes.isNotEmpty() && uiState !is HubUiState.Loading) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                scenes.forEachIndexed { idx, _ ->
                                    val isActive = idx == activeFrameIndex
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(if (isActive) 10.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) CosmicPrimary else MutedText)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isPlaying) {
                                            viewModel.stopVideoPlayback()
                                        } else {
                                            viewModel.playVideo()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("video_playback_toggle_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPlaying) CosmicAccent else CosmicPrimary,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Playback toggle"
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isPlaying) "Stop Waves" else "Play Video", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { viewModel.saveVideoToHistory(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_video_project_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CosmicSurfaceVariant,
                                        contentColor = White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save Project")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Movie", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "💡 Headphone Warning: Playback generates live analog ambient drones matching your theme.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// WORKSPACE 3: FACE SWAP STUDIO
// ----------------------------------------------------
@Composable
fun FaceSwapStudioWorkspace(viewModel: CreativeHubViewModel) {
    val context = LocalContext.current
    val presets by viewModel.presetTemplates.collectAsStateWithLifecycle()
    val activePreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val customUri by viewModel.customTargetUri.collectAsStateWithLifecycle()
    
    val faceUri by viewModel.sourceFaceUri.collectAsStateWithLifecycle()
    val faceBitmap by viewModel.sourceFaceBitmap.collectAsStateWithLifecycle()
    val targetBitmap by viewModel.targetBaseBitmap.collectAsStateWithLifecycle()
    val compositeResult by viewModel.swappedBitmapResult.collectAsStateWithLifecycle()
    val uiState by viewModel.faceSwapUiState.collectAsStateWithLifecycle()

    val xOffset by viewModel.faceXOffset.collectAsStateWithLifecycle()
    val yOffset by viewModel.faceYOffset.collectAsStateWithLifecycle()
    val scale by viewModel.faceScale.collectAsStateWithLifecycle()
    val rotation by viewModel.faceRotation.collectAsStateWithLifecycle()
    val feather by viewModel.faceFeather.collectAsStateWithLifecycle()
    val brightness by viewModel.faceBrightness.collectAsStateWithLifecycle()
    val contrast by viewModel.faceContrast.collectAsStateWithLifecycle()
    
    val colorR by viewModel.faceColorR.collectAsStateWithLifecycle()
    val colorG by viewModel.faceColorG.collectAsStateWithLifecycle()
    val colorB by viewModel.faceColorB.collectAsStateWithLifecycle()

    val sourceFacePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setSourceFace(it, context) }
    }

    val customBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.selectCustomTarget(it, context) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Face Swap Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CosmicPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Compose faces into artistic avatars locally on-device",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1.5f)
                    .height(110.dp)
                    .border(BorderStroke(1.dp, if (faceUri != null) CosmicPrimary else CardBorder), RoundedCornerShape(12.dp))
                    .clickable { sourceFacePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .testTag("pick_source_face_button"),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (faceBitmap != null) {
                        Image(
                            bitmap = faceBitmap!!.asImageBitmap(),
                            contentDescription = "User face photo",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, CosmicPrimary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Face Selected", color = CosmicPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add face", tint = CosmicPrimary, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Choose Face", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1.5f)
                    .height(110.dp)
                    .border(BorderStroke(1.dp, if (customUri != null) CosmicAccent else CardBorder), RoundedCornerShape(12.dp))
                    .clickable { customBackgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .testTag("pick_custom_background_button"),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (customUri != null && targetBitmap != null) {
                        Image(
                            bitmap = targetBitmap!!.asImageBitmap(),
                            contentDescription = "User background base",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Custom Base", color = CosmicAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.UploadFile, contentDescription = "Add scene background", tint = CosmicAccent, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Custom Scene", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Text(
            text = "Select Masterpiece Preset Template",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(presets) { preset ->
                val isSelected = activePreset?.id == preset.id
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CosmicSurface)
                        .border(
                            BorderStroke(2.dp, if (isSelected) CosmicPrimary else Color.Transparent),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.selectPreset(preset, context) }
                ) {
                    AsyncImage(
                        model = preset.imageUrl,
                        contentDescription = preset.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isSelected) CosmicPrimary else White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.executeFaceSwapRenderer() },
            enabled = faceBitmap != null && targetBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("render_faceswap_button")
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CosmicPrimary,
                contentColor = Color.Black,
                disabledContainerColor = CosmicSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cached, contentDescription = "Blend Swap")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Process Face-Swap Composite", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (targetBitmap != null) {
            Text(
                text = "Interactivity Placement Workbench",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (compositeResult != null && uiState !is HubUiState.Loading) {
                            Image(
                                bitmap = compositeResult!!.asImageBitmap(),
                                contentDescription = "Active face composite",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (uiState is HubUiState.Loading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CosmicPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Compositing filters...", color = CosmicPrimary)
                            }
                        } else {
                            Image(
                                bitmap = targetBitmap!!.asImageBitmap(),
                                contentDescription = "Target background",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .graphicsLayer {
                                        translationX = (xOffset - 0.5f) * 320f
                                        translationY = (yOffset - 0.5f) * 380f
                                        scaleX = scale
                                        scaleY = scale
                                        rotationZ = rotation
                                    }
                                    .border(1.5.dp, CosmicPrimary, CircleShape)
                                    .background(CosmicPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("FACE", color = CosmicPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Fine Tuning Controllers", color = CosmicPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Center X Offset (${(xOffset * 100).toInt()}%):", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                            Slider(
                                value = xOffset,
                                onValueChange = { 
                                    viewModel.faceXOffset.value = it
                                    viewModel.executeFaceSwapRenderer()
                                },
                                valueRange = 0.1f..0.9f,
                                colors = SliderDefaults.colors(thumbColor = CosmicPrimary, activeTrackColor = CosmicPrimary)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Center Y Offset (${(yOffset * 100).toInt()}%):", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                            Slider(
                                value = yOffset,
                                onValueChange = { 
                                    viewModel.faceYOffset.value = it
                                    viewModel.executeFaceSwapRenderer()
                                },
                                valueRange = 0.1f..0.9f,
                                colors = SliderDefaults.colors(thumbColor = CosmicPrimary, activeTrackColor = CosmicPrimary)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Face Diameter Size (${(scale * 100).toInt()}%):", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                            Slider(
                                value = scale,
                                onValueChange = { 
                                    viewModel.faceScale.value = it
                                    viewModel.executeFaceSwapRenderer()
                                },
                                valueRange = 0.3f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = CosmicPrimary, activeTrackColor = CosmicPrimary)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Angle Rotation (${rotation.toInt()}°):", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                            Slider(
                                value = rotation,
                                onValueChange = { 
                                    viewModel.faceRotation.value = it
                                    viewModel.executeFaceSwapRenderer()
                                },
                                valueRange = -45f..45f,
                                colors = SliderDefaults.colors(thumbColor = CosmicPrimary, activeTrackColor = CosmicPrimary)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Edge Soft Blur (${(feather * 100).toInt()}%):", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                            Slider(
                                value = feather,
                                onValueChange = { 
                                    viewModel.faceFeather.value = it
                                    viewModel.executeFaceSwapRenderer()
                                },
                                valueRange = 0.05f..0.7f,
                                colors = SliderDefaults.colors(thumbColor = CosmicPrimary, activeTrackColor = CosmicPrimary)
                            )
                        }

                        var showAdvancedColorMatcher by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { showAdvancedColorMatcher = !showAdvancedColorMatcher },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                if (showAdvancedColorMatcher) "Hide Advanced Skin Tone Matching" else "Show Advanced Skin Tone Matching",
                                color = CosmicPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AnimatedVisibility(visible = showAdvancedColorMatcher) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Brightness shift:", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Slider(
                                        value = brightness,
                                        onValueChange = { 
                                            viewModel.faceBrightness.value = it
                                            viewModel.executeFaceSwapRenderer()
                                        },
                                        valueRange = -80f..80f,
                                        colors = SliderDefaults.colors(thumbColor = CosmicAccent, activeTrackColor = CosmicAccent)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Contrast weighting:", color = White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Slider(
                                        value = contrast,
                                        onValueChange = { 
                                            viewModel.faceContrast.value = it
                                            viewModel.executeFaceSwapRenderer()
                                        },
                                        valueRange = 0.6f..1.5f,
                                        colors = SliderDefaults.colors(thumbColor = CosmicAccent, activeTrackColor = CosmicAccent)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Match Skin Red:", color = Color(0xFFFF4D4D), fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Slider(
                                        value = colorR,
                                        onValueChange = { 
                                            viewModel.faceColorR.value = it
                                            viewModel.executeFaceSwapRenderer()
                                        },
                                        valueRange = 0.7f..1.3f,
                                        colors = SliderDefaults.colors(thumbColor = CosmicAccent, activeTrackColor = CosmicAccent)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Match Skin Green:", color = Color(0xFF4DFF4D), fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Slider(
                                        value = colorG,
                                        onValueChange = { 
                                            viewModel.faceColorG.value = it
                                            viewModel.executeFaceSwapRenderer()
                                        },
                                        valueRange = 0.7f..1.3f,
                                        colors = SliderDefaults.colors(thumbColor = CosmicAccent, activeTrackColor = CosmicAccent)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Match Skin Blue:", color = Color(0xFF4D4DFF), fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Slider(
                                        value = colorB,
                                        onValueChange = { 
                                            viewModel.faceColorB.value = it
                                            viewModel.executeFaceSwapRenderer()
                                        },
                                        valueRange = 0.7f..1.3f,
                                        colors = SliderDefaults.colors(thumbColor = CosmicAccent, activeTrackColor = CosmicAccent)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.saveSwappedFaceToHistory(context) },
                            enabled = compositeResult != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_faceswap_button")
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CosmicSecondary,
                                contentColor = White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Gallery Save")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keep Composite in Gallery", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// WORKSPACE 4: CREATION VAULT GALLERY
// ----------------------------------------------------
@Composable
fun CreationGalleryWorkspace(viewModel: CreativeHubViewModel) {
    val items by viewModel.creationHistory.collectAsStateWithLifecycle()
    var selectedItemForZoom by remember { mutableStateOf<Creation?>(null) }
    var selectedFilterType by remember { mutableStateOf<CreationType?>(null) }

    val filteredItems = remember(items, selectedFilterType) {
        if (selectedFilterType == null) items else items.filter { it.type == selectedFilterType }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Creation Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CosmicPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Review, loop, and stream all compiled designs",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomAStyleChip(text = "All", isSelected = selectedFilterType == null, onClick = { selectedFilterType = null })
            CustomAStyleChip(text = "Images", isSelected = selectedFilterType == CreationType.IMAGE, onClick = { selectedFilterType = CreationType.IMAGE })
            CustomAStyleChip(text = "Videos", isSelected = selectedFilterType == CreationType.VIDEO, onClick = { selectedFilterType = CreationType.VIDEO })
            CustomAStyleChip(text = "Swaps", isSelected = selectedFilterType == CreationType.FACESWAP, onClick = { selectedFilterType = CreationType.FACESWAP })
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        Icons.Default.DeveloperBoard,
                        contentDescription = "Empty History",
                        tint = MutedText.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "History is empty",
                        color = White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your generated creations will persist securely in Room database storage here.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("gallery_grid"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems.size) { index ->
                    val item = filteredItems[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clickable { selectedItemForZoom = item }
                            .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp))
                            .testTag("creation_item_card_${item.id}"),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = if (item.fileUri.isNotEmpty()) item.fileUri else "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=300",
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.8f)
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when (item.type) {
                                                CreationType.IMAGE -> "AI Image"
                                                CreationType.VIDEO -> "AI Video Movie"
                                                CreationType.FACESWAP -> "Face Swap"
                                            },
                                            color = CosmicPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteCreation(item) },
                                        modifier = Modifier
                                            .size(26.dp)
                                            .testTag("delete_creation_${item.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete creation",
                                            tint = CosmicAccent,
                                            modifier = Modifier.size(16.dp)
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

    selectedItemForZoom?.let { item ->
        ZoomOverlayDialog(
            creation = item,
            onClose = { 
                AudioSynth.stop()
                selectedItemForZoom = null 
            }
        )
    }
}

@Composable
fun ZoomOverlayDialog(creation: Creation, onClose: () -> Unit) {
    var isPlayingMovieProject by remember { mutableStateOf(false) }
    var movieScenes by remember { mutableStateOf<List<StoryboardScene>>(emptyList()) }
    var movieUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeMovieFrameIndex by remember { mutableStateOf(0) }

    LaunchedEffect(creation) {
        if (creation.type == CreationType.VIDEO && creation.configurationJson.isNotEmpty()) {
            try {
                val json = JSONObject(creation.configurationJson)
                val scenesArray = json.optJSONArray("scenes")
                
                if (scenesArray != null && scenesArray.length() > 0) {
                    val sList = mutableListOf<StoryboardScene>()
                    val uList = mutableListOf<String>()
                    
                    for (i in 0 until scenesArray.length()) {
                        val sObj = scenesArray.getJSONObject(i)
                        sList.add(
                            StoryboardScene(
                                sceneNumber = sObj.optInt("number", i + 1),
                                scenePrompt = sObj.optString("prompt"),
                                subtitle = sObj.optString("subtitle")
                            )
                        )
                        uList.add(sObj.optString("url"))
                    }
                    movieScenes = sList
                    movieUrls = uList
                }
            } catch (e: Exception) {
                // Parsing failed
            }
        }
    }

    LaunchedEffect(isPlayingMovieProject) {
        if (isPlayingMovieProject && movieScenes.isNotEmpty()) {
            while (isPlayingMovieProject) {
                delay(4000)
                activeMovieFrameIndex = (activeMovieFrameIndex + 1) % movieScenes.size
            }
        }
    }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .testTag("zoom_dialog_card")
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicSurfaceVariant)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = creation.title,
                        color = CosmicPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { onClose() },
                        modifier = Modifier.size(28.dp).testTag("close_zoom_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close overlay", tint = White)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlayingMovieProject && movieScenes.isNotEmpty() && movieUrls.isNotEmpty()) {
                        val currentScene = movieScenes.getOrNull(activeMovieFrameIndex)
                        val url = movieUrls.getOrNull(activeMovieFrameIndex) ?: ""
                        
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Running Frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            currentScene?.let { scene ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = scene.subtitle,
                                        color = CosmicPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        AsyncImage(
                            model = if (creation.fileUri.isNotEmpty()) creation.fileUri else "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=600",
                            contentDescription = "Full render",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Creation Details",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Prompt: " + creation.prompt,
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (creation.type == CreationType.VIDEO && movieScenes.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (isPlayingMovieProject) {
                                    isPlayingMovieProject = false
                                    AudioSynth.stop()
                                } else {
                                    isPlayingMovieProject = true
                                    try {
                                        val json = JSONObject(creation.configurationJson)
                                        val themeName = json.optString("theme", "SCIFI_SPACE")
                                        val selectedTheme = VideoTheme.valueOf(themeName)
                                        AudioSynth.start(selectedTheme)
                                    } catch (e: Exception) {
                                        AudioSynth.start(VideoTheme.SCIFI_SPACE)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("timeline_zoom_player_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingMovieProject) CosmicAccent else CosmicPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(if (isPlayingMovieProject) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Loop")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isPlayingMovieProject) "Pause Frame Loop" else "Simulate Video Reels Player",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
