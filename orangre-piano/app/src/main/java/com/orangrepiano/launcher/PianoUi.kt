package com.orangrepiano.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF151311)
private val Orange = Color(0xFFE7A17C)
private val Peach = Color(0xFFF4C6A6)
private val Cream = Color(0xFFF6E9D9)
private val Subtle = Color(0xFFBCA08E)
private val Line = Color(0x44F4C6A6)
private val Glass = Color(0x99302620)
private val NoGlass = Color(0xE8151311)

private enum class Page { HOME, DRAWER, SETTINGS }
private fun tr(lang: String, en: String, ru: String): String = if (lang == "ru") ru else en

@Composable
fun PianoApp(onRequestHome: () -> Unit, onStatusBarChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs(context) }
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(installedApps(context)) }
    var favorites by remember { mutableStateOf(prefs.favorites) }
    var slots by remember { mutableIntStateOf(prefs.slots) }
    var glass by remember { mutableStateOf(prefs.glass) }
    var quiet by remember { mutableStateOf(prefs.quiet) }
    var hideFavorites by remember { mutableStateOf(prefs.hideFavoritesInDrawer) }
    var language by remember { mutableStateOf(prefs.language) }
    var hideStatusBar by remember { mutableStateOf(prefs.hideStatusBar) }
    var wallpaper by remember { mutableStateOf(getWallpaper(context)) }
    var page by remember { mutableStateOf(Page.HOME) }
    var filter by remember { mutableStateOf("") }
    var selectingSlot by remember { mutableIntStateOf(-1) }
    var pickerSearch by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val cameraManager = remember(context) {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val flashCameraId = remember(cameraManager) {
        try {
            val flashDevices = cameraManager.cameraIdList.filter { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            flashDevices.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: flashDevices.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
    var torchEnabled by remember { mutableStateOf(false) }

    DisposableEffect(cameraManager, flashCameraId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == flashCameraId) torchEnabled = enabled
            }
            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == flashCameraId) torchEnabled = false
            }
        }
        try { cameraManager.registerTorchCallback(context.mainExecutor, callback) }
        catch (_: Exception) { }
        onDispose {
            try { cameraManager.unregisterTorchCallback(callback) }
            catch (_: Exception) { }
        }
    }

    fun toggleTorch() {
        if (flashCameraId == null) {
            Toast.makeText(context, tr(language, "No flashlight available", "Фонарик недоступен"), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            cameraManager.setTorchMode(flashCameraId, !torchEnabled)
        } catch (_: Exception) {
            Toast.makeText(context,
                tr(language, "Flashlight is busy or unavailable", "Фонарик занят или недоступен"),
                Toast.LENGTH_SHORT).show()
        }
    }
    val torchPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleTorch()
        else Toast.makeText(context,
            tr(language, "Camera permission needed for flashlight", "Для фонарика требуется разрешение камеры"),
            Toast.LENGTH_SHORT).show()
    }
    fun requestTorch() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            toggleTorch()
        } else {
            torchPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installed = installedApps(context)
                favorites = prefs.favorites
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { importWallpaper(context, uri) }
            if (result != null) wallpaper = result
            else Toast.makeText(context, tr(language, "Could not read wallpaper", "Не удалось открыть обои"), Toast.LENGTH_SHORT).show()
        }
    }

    fun updateFavorite(index: Int, key: String) {
        prefs.setFavorite(index, key)
        favorites = prefs.favorites
    }
    fun chooseFavorite(index: Int) {
        selectingSlot = index
        pickerSearch = ""
    }
    fun goHome() { page = Page.HOME; filter = "" }
    fun goDrawer() { page = Page.DRAWER }

    BackHandler(enabled = page != Page.HOME || selectingSlot >= 0) {
        if (selectingSlot >= 0) selectingSlot = -1 else goHome()
    }

    LaunchedEffect(page) {
        if (page != Page.SETTINGS) {
            try { focus.requestFocus() } catch (_: Exception) { }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Orange, onPrimary = Ink,
        background = Ink, onBackground = Cream,
        surface = Color(0xFF302720), onSurface = Cream
    )) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .onKeyEvent { event ->
                    if (page == Page.SETTINGS || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (event.key == Key.Escape) {
                        goHome()
                        return@onKeyEvent true
                    }
                    if (event.key == Key.Backspace && page == Page.DRAWER && filter.isNotEmpty()) {
                        filter = filter.dropLast(1)
                        return@onKeyEvent true
                    }
                    val native = event.nativeKeyEvent
                    if (native.isCtrlPressed || native.isAltPressed || native.isMetaPressed) return@onKeyEvent false
                    val code = native.unicodeChar
                    if (code in 33..0x10FFFF && Character.isValidCodePoint(code) && !Character.isISOControl(code)) {
                        val typed = String(Character.toChars(code))
                        if (page == Page.HOME) {
                            filter = typed
                            goDrawer()
                        } else {
                            filter += typed
                        }
                        true
                    } else false
                }
                .focusRequester(focus)
                .focusable()
        ) {
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            PianoAtmosphere(wallpaperPresent = wallpaper != null)
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (wallpaper == null) listOf(Color(0x110A090B),Color.Transparent,Color(0x42100D10))
                    else listOf(Ink.copy(alpha = .48f),Ink.copy(alpha = .35f),Ink.copy(alpha = .79f))
                )
            ))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (quiet) {
                        fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                    } else if (initialState == Page.HOME && targetState == Page.DRAWER) {
                        (slideInVertically(animationSpec = tween(265), initialOffsetY = { it }) +
                            fadeIn(tween(210))) togetherWith
                            (slideOutVertically(animationSpec = tween(265), targetOffsetY = { -it / 5 }) +
                                fadeOut(tween(210)))
                    } else if (initialState == Page.DRAWER && targetState == Page.HOME) {
                        (slideInVertically(animationSpec = tween(265), initialOffsetY = { -it / 5 }) +
                            fadeIn(tween(215))) togetherWith
                            (slideOutVertically(animationSpec = tween(265), targetOffsetY = { it }) +
                                fadeOut(tween(215)))
                    } else {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(130))
                    }
                },
                label = "piano_navigation",
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) { current ->
                when (current) {
                    Page.HOME -> HomePage(
                        apps = installed, favorites = favorites, slots = slots, glass = glass,
                        language = language, quiet = quiet,
                        onClock = { openDefaultClock(context, language) },
                        onCalendar = { openDefaultCalendar(context, language) },
                        onCamera = { openDefaultCamera(context, language) },
                        onTorch = ::requestTorch,
                        torchEnabled = torchEnabled,
                        onOpen = { openApp(context, it) },
                        onChoose = ::chooseFavorite,
                        onDrawer = ::goDrawer,
                        onSettings = { page = Page.SETTINGS }
                    )
                    Page.DRAWER -> DrawerPage(
                        apps = installed.filterNot { hideFavorites && it.key in favorites },
                        filter = filter, onFilter = { filter = it },
                        language = language, quiet = quiet,
                        glass = glass, onHome = ::goHome,
                        onOpen = { openApp(context, it) }
                    )
                    Page.SETTINGS -> SettingsPage(
                        apps = installed, favorites = favorites, slots = slots,
                        glass = glass, quiet = quiet, hideFavorites = hideFavorites,
                        hasWallpaper = wallpaper != null,
                        language = language, hideStatusBar = hideStatusBar,
                        onLanguage = { prefs.setLanguage(it); language = prefs.language },
                        onHideStatusBar = { prefs.setHideStatusBar(it); hideStatusBar = it; onStatusBarChanged(it) },
                        onBack = ::goHome,
                        onChoose = ::chooseFavorite,
                        onMove = { index, delta ->
                            prefs.moveFavorite(index, delta)
                            favorites = prefs.favorites
                        },
                        onSlots = { n ->
                            prefs.setSlots(n); slots = prefs.slots
                            favorites = prefs.favorites
                        },
                        onGlass = { prefs.setGlass(it); glass = it },
                        onQuiet = { prefs.setQuiet(it); quiet = it },
                        onHideFavorites = { prefs.setHideFavorites(it); hideFavorites = it },
                        onWallpaper = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onClearWallpaper = {
                            removeWallpaper(context)
                            wallpaper = null
                        },
                        onSetHome = onRequestHome
                    )
                }
            }
        }

        if (selectingSlot >= 0) {
            val choices = installed.filter {
                it.label.contains(pickerSearch, ignoreCase = true)
            }
            AlertDialog(
                onDismissRequest = { selectingSlot = -1 },
                title = { Text(tr(language, "SELECT AN APP", "ВЫБРАТЬ ПРИЛОЖЕНИЕ"), color = Peach, fontFamily = FontFamily.Monospace, fontSize = 17.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = pickerSearch,
                            onValueChange = { pickerSearch = it },
                            label = { Text(tr(language, "Filter apps", "Поиск приложений")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(choices, key = { it.key }) { app ->
                                Text(
                                    text = app.label,
                                    color = Cream,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        updateFavorite(selectingSlot, app.key)
                                        selectingSlot = -1
                                    }.padding(vertical = 12.dp)
                                )
                                HorizontalDivider(color = Line.copy(alpha = .4f))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        updateFavorite(selectingSlot, "")
                        selectingSlot = -1
                    }) { Text(tr(language, "CLEAR SLOT", "ОЧИСТИТЬ")) }
                },
                dismissButton = {
                    TextButton(onClick = { selectingSlot = -1 }) { Text(tr(language, "CANCEL", "ОТМЕНА")) }
                },
                containerColor = Color(0xFF29201C)
            )
        }
    }
}

@Composable
private fun PixelMotif() {
    // Small decorative squares, deliberately not app icons.
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 82.dp, end = 20.dp),
        horizontalAlignment = Alignment.End
    ) {
        val shades = listOf(Orange.copy(alpha = .18f), Orange.copy(alpha = .10f), Orange.copy(alpha = .05f))
        shades.forEachIndexed { i, shade ->
            Row {
                repeat(3 - i) {
                    Box(Modifier.padding(3.dp).size(7.dp).background(shade))
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PianoGlassSurface(modifier = modifier, enabled = enabled) {
        Box(Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(
    apps: List<AppEntry>,
    favorites: List<String>,
    slots: Int,
    glass: Boolean,
    language: String,
    quiet: Boolean,
    onClock: () -> Unit,
    onCalendar: () -> Unit,
    onCamera: () -> Unit,
    onTorch: () -> Unit,
    torchEnabled: Boolean,
    onOpen: (AppEntry) -> Unit,
    onChoose: (Int) -> Unit,
    onDrawer: () -> Unit,
    onSettings: () -> Unit
) {
    var swipe by remember { mutableStateOf(0f) }
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { swipe = 0f },
                onVerticalDrag = { _, amount -> swipe += amount },
                onDragEnd = { if (swipe < -75f) onDrawer(); swipe = 0f }
            )
        }
    ) {
        // Titan 2's 1:1 display has limited usable dp height after Android insets.
        // Fit the entire home without vertical scrolling, including all six rows.
        val compact = maxHeight < 445.dp
        val lineHeight = when {
            maxHeight < 375.dp -> 29.dp
            compact -> 33.dp
            else -> 38.dp
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = if (compact) 15.dp else 21.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClockBlock(compact = compact, language = language, quiet = quiet, onClock = onClock, onCalendar = onCalendar)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PianoSquareButton(
                        icon = PianoIcon.Camera,
                        description = tr(language, "Open camera", "Открыть камеру"),
                        onClick = onCamera, edge = if (compact) 37.dp else 41.dp
                    )
                    PianoSquareButton(
                        icon = PianoIcon.Flashlight,
                        description = tr(language,
                            if (torchEnabled) "Turn off flashlight" else "Turn on flashlight",
                            if (torchEnabled) "Выключить фонарик" else "Включить фонарик"),
                        onClick = onTorch,
                        active = torchEnabled,
                        edge = if (compact) 37.dp else 41.dp
                    )
                }
            }
            GlassPanel(enabled = glass, modifier = Modifier.fillMaxWidth()) {
                Column {
                    repeat(slots) { index ->
                        val entry = apps.firstOrNull { it.key == favorites.getOrNull(index) }
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        // ATELIER polish: the number and the copper marker are isolated
                        // from the glass name surface. Every row shares the same quiet
                        // material: no alternating full-width tint or colliding edges.
                        val insetScale by animateFloatAsState(
                            targetValue = if (pressed && !quiet) .985f else 1f,
                            animationSpec = tween(if (quiet) 1 else 145, easing = FastOutSlowInEasing),
                            label = "favorite_label_press"
                        )
                        val insetTint by animateColorAsState(
                            targetValue = if (pressed) Color(0x594F362F) else Color(0x0CFFF2E3),
                            animationSpec = tween(if (quiet) 1 else 190),
                            label = "favorite_label_tint"
                        )
                        val insetBorder by animateColorAsState(
                            targetValue = if (pressed) PianoTone.peach.copy(alpha = .42f)
                                else PianoTone.peach.copy(alpha = .065f),
                            animationSpec = tween(if (quiet) 1 else 190),
                            label = "favorite_label_edge"
                        )
                        val insetShape = RoundedCornerShape(8.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeight)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = { if (entry == null) onChoose(index) else onOpen(entry) },
                                    onLongClick = { onChoose(index) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (index + 1).toString().padStart(2, '0'),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = PianoTone.copper.copy(alpha = .70f),
                                modifier = Modifier.width(30.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .padding(vertical = 2.dp)
                                    .graphicsLayer(scaleX = insetScale, scaleY = insetScale)
                                    .clip(insetShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(insetTint, insetTint.copy(alpha = .66f))
                                        )
                                    )
                                    .border(1.dp, insetBorder, insetShape)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    entry?.label ?: tr(
                                        language,
                                        "+  assign application",
                                        "+  выбрать приложение"
                                    ),
                                    color = if (entry == null) Subtle.copy(alpha = .70f) else Cream,
                                    fontSize = if (compact) 16.sp else 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier.size(5.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (entry == null) Color.Transparent
                                        else if (pressed) PianoTone.pearl
                                        else PianoTone.copper.copy(alpha = .78f)
                                    )
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr(language, "↑  ALL APPS", "↑  ВСЕ ПРИЛОЖЕНИЯ"), fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Peach,
                    modifier = Modifier.clickable(onClick = onDrawer).padding(vertical = 6.dp))
                Text(tr(language, "SETTINGS  ⋮", "НАСТРОЙКИ  ⋮"), fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = Subtle,
                    modifier = Modifier.clickable(onClick = onSettings).padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ClockBlock(
    compact: Boolean,
    language: String,
    quiet: Boolean,
    onClock: () -> Unit,
    onCalendar: () -> Unit
) {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(10_000) }
    }
    val locale = if (language == "ru") Locale.forLanguageTag("ru") else Locale.ENGLISH
    Column {
        PianoPixelClock(
            time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            compact = compact,
            quiet = quiet,
            description = tr(language, "Open clock", "Открыть часы"),
            onClick = onClock
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE  ·  d MMMM", locale)).uppercase(locale),
            color = Subtle, fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, letterSpacing = .5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onCalendar).padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun DrawerPage(
    apps: List<AppEntry>,
    filter: String,
    onFilter: (String) -> Unit,
    language: String,
    quiet: Boolean,
    glass: Boolean,
    onHome: () -> Unit,
    onOpen: (AppEntry) -> Unit
) {
    val results = remember(apps, filter) {
        apps.filter { filter.isBlank() || it.label.contains(filter, ignoreCase = true) }
    }
    val groups = remember(results) { results.groupBy {
        val first = it.label.trim().firstOrNull()?.uppercaseChar()
        if (first?.isLetter() == true) first.toString() else "#"
    } }
    val keys = groups.keys.toList()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var previewLetter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(previewLetter) {
        if (previewLetter != null) {
            delay(if (quiet) 350 else 620)
            previewLetter = null
        }
    }
    val indices = remember(groups) {
        buildMap {
            var index = 0
            groups.forEach { (letter, entries) ->
                put(letter, index)
                index += entries.size + 1
            }
        }
    }
    val currentLetter by remember(list, keys, indices) {
        androidx.compose.runtime.derivedStateOf {
            keys.lastOrNull { (indices[it] ?: 0) <= list.firstVisibleItemIndex }
                ?: keys.firstOrNull()
        }
    }
    fun navigateLetter(letter: String) {
        val index = indices[letter] ?: return
        previewLetter = letter
        scope.launch {
            val distance = kotlin.math.abs(index - list.firstVisibleItemIndex)
            if (quiet || distance > 36) {
                list.scrollToItem(index)
            } else {
                list.animateScrollToItem(index)
            }
        }
    }

    // Observe finger movement before child scrolling consumes it. Close only when
    // the alphabetical list was already at its beginning as the swipe began.
    // A swipe inside a scrolled list continues normal list navigation.
    val downwardToHome = Modifier.pointerInput(list, onHome) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startedAtTop = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
            var verticalTravel = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!pointer.pressed) break
                verticalTravel += pointer.position.y - pointer.previousPosition.y
            } while (true)
            if (startedAtTop && verticalTravel > 82.dp.toPx()) onHome()
        }
    }
    Column(Modifier.fillMaxSize().then(downwardToHome)
        .padding(start = 18.dp, end = 11.dp, top = 5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            OutlinedTextField(
                value = filter, onValueChange = onFilter, singleLine = true,
                label = { Text(tr(language, "Search / type to filter", "Поиск по названию"), fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PianoTone.peach,
                    unfocusedBorderColor = PianoTone.border.copy(alpha = .50f),
                    focusedTextColor = PianoTone.pearl,
                    unfocusedTextColor = PianoTone.pearl,
                    focusedLabelColor = PianoTone.peach,
                    unfocusedLabelColor = PianoTone.secondary,
                    cursorColor = PianoTone.peach
                )
            )
            PianoSquareButton(
                icon = PianoIcon.Back,
                description = tr(language, "Back to home", "На главный экран"),
                onClick = onHome,
                edge = 40.dp
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), state = list) {
                    groups.forEach { (letter, entries) ->
                        item(key = "head_" + letter) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp)) {
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp))
                                    .background(PianoTone.copper))
                                Spacer(Modifier.width(8.dp))
                                Text(letter, fontFamily = FontFamily.Monospace, color = PianoTone.peach,
                                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f).height(1.dp)
                                    .background(Brush.horizontalGradient(
                                        listOf(PianoTone.border.copy(alpha = .38f), Color.Transparent))))
                            }
                        }
                        items(entries, key = { it.key }) { app ->
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val tint by animateColorAsState(
                                if (pressed) Color(0x775F4037) else Color.Transparent,
                                animationSpec = tween(if (quiet) 1 else 160),
                                label = "app_row_tint"
                            )
                            val rowScale by animateFloatAsState(
                                if (pressed && !quiet) .974f else 1f,
                                animationSpec = tween(if (quiet) 1 else 155), label = "app_row_press"
                            )
                            Box(
                                Modifier.fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tint)
                                    .graphicsLayer(scaleX = rowScale, scaleY = rowScale)
                                    .clickable(interactionSource = interaction, indication = null) { onOpen(app) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Text(app.label, color = PianoTone.pearl, fontSize = 16.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Normal)
                            }
                        }
                    }
                    if (results.isEmpty()) item {
                        Text(tr(language, "NO MATCHES", "НИЧЕГО НЕ НАЙДЕНО"),
                            color = Subtle, modifier = Modifier.padding(top = 30.dp))
                    }
                }
                PianoLetterRail(
                    letters = keys,
                    selected = previewLetter ?: currentLetter,
                    quiet = quiet,
                    onLetter = ::navigateLetter
                )
            }
            PianoLetterPreview(
                letter = previewLetter, quiet = quiet,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 54.dp)
            )
        }
    }
}

@Composable
private fun SettingsPage(
    apps: List<AppEntry>,
    favorites: List<String>,
    slots: Int,
    glass: Boolean,
    quiet: Boolean,
    hideFavorites: Boolean,
    hasWallpaper: Boolean,
    language: String,
    hideStatusBar: Boolean,
    onLanguage: (String) -> Unit,
    onHideStatusBar: (Boolean) -> Unit,
    onBack: () -> Unit,
    onChoose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSlots: (Int) -> Unit,
    onGlass: (Boolean) -> Unit,
    onQuiet: (Boolean) -> Unit,
    onHideFavorites: (Boolean) -> Unit,
    onWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSetHome: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 19.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(tr(language, "PREFERENCES", "НАСТРОЙКИ"), color = Peach,
                fontFamily = FontFamily.Monospace,
                fontSize = 23.sp, fontWeight = FontWeight.Bold)
            PianoSquareButton(
                icon = PianoIcon.Back,
                description = tr(language, "Back to home", "На главный экран"),
                onClick = onBack,
                edge = 40.dp
            )
        }
        Spacer(Modifier.height(17.dp))
        SectionTitle(tr(language, "01 / FAVORITE KEYS", "01 / ИЗБРАННОЕ"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(language, "SHOW", "ПОКАЗАТЬ"), color = Subtle, fontSize = 12.sp)
            listOf(5, 6).forEach { n ->
                TextButton(onClick = { onSlots(n) }) {
                    Text(n.toString(), color = if (n == slots) Orange else Subtle, fontSize = 16.sp)
                }
            }
        }
        PianoGlassSurface(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
           repeat(slots) { index ->
            val label = apps.firstOrNull { it.key == favorites.getOrNull(index) }?.label ?: tr(language, "+ choose app", "+ выбрать приложение")
            Row(
                Modifier.fillMaxWidth().height(39.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text((index + 1).toString().padStart(2, '0'), color = Orange,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    modifier = Modifier.width(30.dp))
                Text(label, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { onChoose(index) })
                Text("↑", color = if (index > 0) Orange else Subtle.copy(alpha = .35f),
                    modifier = Modifier.clickable(enabled = index > 0) { onMove(index, -1) }.padding(8.dp))
                Text("↓", color = if (index < slots - 1) Orange else Subtle.copy(alpha = .35f),
                    modifier = Modifier.clickable(enabled = index < slots - 1) { onMove(index, 1) }.padding(8.dp))
            }
            if (index != slots - 1) HorizontalDivider(color = PianoTone.border.copy(alpha = .23f))
           }
          }
        }
        Spacer(Modifier.height(19.dp))
        SectionTitle(tr(language, "02 / APPEARANCE", "02 / ОФОРМЛЕНИЕ"))
        SettingRow(tr(language, "GLASS PANELS", "СТЕКЛЯННЫЕ ПАНЕЛИ"), glass, onGlass)
        SettingRow(tr(language, "QUIET ANIMATIONS", "МИНИМУМ АНИМАЦИЙ"), quiet, onQuiet)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onWallpaper) { Text(tr(language, "CHOOSE WALLPAPER", "ВЫБРАТЬ ОБОИ"), color = Orange) }
            if (hasWallpaper) TextButton(onClick = onClearWallpaper) { Text(tr(language, "REMOVE", "УДАЛИТЬ"), color = Subtle) }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle(tr(language, "03 / DIGITAL DETOX", "03 / DIGITAL DETOX"))
        SettingRow(tr(language, "HIDE FAVORITES IN A-Z", "СКРЫТЬ ИЗБРАННОЕ В СПИСКЕ"), hideFavorites, onHideFavorites)
        Spacer(Modifier.height(13.dp))
        SectionTitle(tr(language, "04 / SYSTEM", "04 / СИСТЕМА"))
        Text(tr(language, "LANGUAGE / ЯЗЫК", "ЯЗЫК / LANGUAGE"),
            color = Cream, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onLanguage("ru") }) {
                Text("Русский", color = if (language == "ru") Peach else Subtle)
            }
            TextButton(onClick = { onLanguage("en") }) {
                Text("English", color = if (language == "en") Peach else Subtle)
            }
        }
        SettingRow(tr(language, "HIDE TOP STATUS BAR", "СКРЫТЬ ВЕРХНЮЮ СТРОКУ"), hideStatusBar, onHideStatusBar)
        Text(
            tr(language,
                "Only the top status bar is hidden on the launcher. Android still allows opening the notification shade with a swipe.",
                "Скрывается только верхняя строка на экране лаунчера. Шторка уведомлений Android по-прежнему открывается свайпом."),
            color = Subtle, fontSize = 11.sp, lineHeight = 15.sp
        )
        TextButton(onClick = onSetHome) { Text(tr(language, "SET AS DEFAULT HOME", "СДЕЛАТЬ ГЛАВНЫМ ЭКРАНОМ"), color = Orange) }
        Text(tr(language, "To switch back, open Android Settings → Default apps → Home app.", "Для возврата: Настройки Android → Приложения по умолчанию → Главный экран."),
            color = Subtle, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(15.dp))
        Text("orangre piano · atelier edition 1.0", fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, color = Subtle.copy(alpha = .65f))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp))
            .background(PianoTone.copper))
        Spacer(Modifier.width(8.dp))
        Text(label, color = PianoTone.peach, fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .65.sp)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f).height(1.dp)
            .background(Brush.horizontalGradient(
                listOf(PianoTone.border.copy(alpha = .36f), Color.Transparent))))
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)
        .clip(RoundedCornerShape(11.dp))
        .background(Color(0x303D302E))
        .padding(horizontal = 10.dp)
        .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = PianoTone.pearl, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PianoTone.pearl,
                checkedTrackColor = PianoTone.copper,
                uncheckedTrackColor = Color(0xFF493A38),
                uncheckedThumbColor = PianoTone.secondary
            )
        )
    }
}
