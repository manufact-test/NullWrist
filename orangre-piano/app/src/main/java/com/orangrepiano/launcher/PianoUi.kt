package com.orangrepiano.launcher

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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

@Composable
fun PianoApp(onRequestHome: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs(context) }
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(installedApps(context)) }
    var favorites by remember { mutableStateOf(prefs.favorites) }
    var slots by remember { mutableIntStateOf(prefs.slots) }
    var glass by remember { mutableStateOf(prefs.glass) }
    var quiet by remember { mutableStateOf(prefs.quiet) }
    var hideFavorites by remember { mutableStateOf(prefs.hideFavoritesInDrawer) }
    var wallpaper by remember { mutableStateOf(getWallpaper(context)) }
    var page by remember { mutableStateOf(Page.HOME) }
    var filter by remember { mutableStateOf("") }
    var selectingSlot by remember { mutableIntStateOf(-1) }
    var pickerSearch by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

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
            else Toast.makeText(context, "Could not read wallpaper", Toast.LENGTH_SHORT).show()
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
            } else {
                Box(Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF100F0E), Color(0xFF302018), Color(0xFF130F0D)))
                ))
                PixelMotif()
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Ink.copy(alpha = .46f), Ink.copy(alpha = .27f), Ink.copy(alpha = .87f)))
            ))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (quiet) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                    else fadeIn(tween(180)) togetherWith fadeOut(tween(130))
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
                        onOpen = { openApp(context, it) },
                        onChoose = ::chooseFavorite,
                        onDrawer = ::goDrawer,
                        onSettings = { page = Page.SETTINGS }
                    )
                    Page.DRAWER -> DrawerPage(
                        apps = installed.filterNot { hideFavorites && it.key in favorites },
                        filter = filter, onFilter = { filter = it },
                        glass = glass, onHome = ::goHome,
                        onOpen = { openApp(context, it) }
                    )
                    Page.SETTINGS -> SettingsPage(
                        apps = installed, favorites = favorites, slots = slots,
                        glass = glass, quiet = quiet, hideFavorites = hideFavorites,
                        hasWallpaper = wallpaper != null,
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
                title = { Text("SELECT AN APP", color = Peach, fontFamily = FontFamily.Monospace, fontSize = 17.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = pickerSearch,
                            onValueChange = { pickerSearch = it },
                            label = { Text("Filter apps") },
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
                    }) { Text("CLEAR SLOT") }
                },
                dismissButton = {
                    TextButton(onClick = { selectingSlot = -1 }) { Text("CANCEL") }
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
private fun BrandHeader(rightLabel: String, onRight: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("orangre", fontFamily = FontFamily.Monospace, color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("  /  PIANO", fontFamily = FontFamily.Monospace, color = Subtle, fontSize = 10.sp)
        }
        Text(rightLabel, fontFamily = FontFamily.Monospace, color = Orange, fontSize = 11.sp,
            modifier = Modifier.clickable(onClick = onRight).padding(vertical = 7.dp, horizontal = 3.dp))
    }
}

@Composable
private fun GlassPanel(enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) Glass else NoGlass)
            .border(1.dp, if (enabled) Line else Line.copy(alpha = .3f), shape)
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(
    apps: List<AppEntry>,
    favorites: List<String>,
    slots: Int,
    glass: Boolean,
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
        val compact = maxHeight < 430.dp
        val lineHeight = if (compact) 31.dp else 40.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (compact) 5.dp else 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BrandHeader("◦ ◦ ◦", onSettings)
            Spacer(Modifier.height(if (compact) 7.dp else 22.dp))
            ClockBlock(compact)
            Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
            Text("YOUR SIX KEYS".replace("SIX", if (slots == 5) "FIVE" else "SIX"),
                color = Subtle, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(7.dp))
            GlassPanel(enabled = glass, modifier = Modifier.fillMaxWidth()) {
                Column {
                    repeat(slots) { index ->
                        val entry = apps.firstOrNull { it.key == favorites.getOrNull(index) }
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeight)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (pressed) Orange.copy(alpha = .16f) else Color.Transparent)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = { if (entry == null) onChoose(index) else onOpen(entry) },
                                    onLongClick = { onChoose(index) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text((index + 1).toString().padStart(2, '0'),
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Orange.copy(alpha = .7f),
                                modifier = Modifier.width(36.dp))
                            Text(entry?.label ?: "+  assign application",
                                color = if (entry == null) Subtle.copy(alpha = .7f) else Cream,
                                fontSize = if (compact) 16.sp else 18.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            if (entry != null) {
                                Box(Modifier.size(4.dp).background(Orange.copy(alpha = .72f)))
                            }
                        }
                        if (index != slots - 1) HorizontalDivider(color = Line.copy(alpha = .27f))
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 13.dp else 27.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↑  ALL APPS", fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Peach,
                    modifier = Modifier.clickable(onClick = onDrawer).padding(vertical = 8.dp))
                Text("SETTINGS  ⋮", fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = Subtle,
                    modifier = Modifier.clickable(onClick = onSettings).padding(vertical = 8.dp))
            }
            if (!compact) {
                Text("type on keyboard to find an app", color = Subtle.copy(alpha = .7f),
                    fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun ClockBlock(compact: Boolean) {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(10_000) }
    }
    Column {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),
            fontSize = if (compact) 53.sp else 66.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-4).sp,
            lineHeight = if (compact) 56.sp else 68.sp,
            color = Peach)
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE  ·  d MMMM", Locale.getDefault()))
                .uppercase(Locale.getDefault()),
            color = Subtle, fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, letterSpacing = .5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DrawerPage(
    apps: List<AppEntry>,
    filter: String,
    onFilter: (String) -> Unit,
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
    val indices = remember(groups) {
        buildMap {
            var index = 0
            groups.forEach { (letter, entries) ->
                put(letter, index)
                index += entries.size + 1
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 11.dp, top = 5.dp)) {
        BrandHeader("← HOME", onHome)
        Spacer(Modifier.height(10.dp))
        Text("ALL APPLICATIONS", color = Peach, fontSize = 21.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("${results.size} AVAILABLE", color = Subtle, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = filter, onValueChange = onFilter, singleLine = true,
            label = { Text("Search / type to filter", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().padding(end = 21.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), state = list) {
                groups.forEach { (letter, entries) ->
                    item(key = "head_" + letter) {
                        Text(letter, fontFamily = FontFamily.Monospace, color = Orange,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
                    }
                    items(entries, key = { it.key }) { app ->
                        Text(app.label, color = Cream, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(app) }
                                .padding(vertical = 8.dp, horizontal = 4.dp))
                        HorizontalDivider(color = Line.copy(alpha = .18f))
                    }
                }
                if (results.isEmpty()) item {
                    Text("NO MATCHES", color = Subtle, modifier = Modifier.padding(top = 30.dp))
                }
            }
            Column(
                Modifier.width(28.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keys.forEach { letter ->
                    Text(letter, fontFamily = FontFamily.Monospace, color = Orange,
                        fontSize = 10.sp, modifier = Modifier
                            .clickable { scope.launch { list.animateScrollToItem(indices[letter] ?: 0) } }
                            .padding(horizontal = 5.dp, vertical = 3.dp))
                }
            }
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
        BrandHeader("← HOME", onBack)
        Spacer(Modifier.height(12.dp))
        Text("PREFERENCES", color = Peach, fontFamily = FontFamily.Monospace,
            fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(17.dp))
        SectionTitle("01 / FAVORITE KEYS")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SHOW", color = Subtle, fontSize = 12.sp)
            listOf(5, 6).forEach { n ->
                TextButton(onClick = { onSlots(n) }) {
                    Text(n.toString(), color = if (n == slots) Orange else Subtle, fontSize = 16.sp)
                }
            }
        }
        repeat(slots) { index ->
            val label = apps.firstOrNull { it.key == favorites.getOrNull(index) }?.label ?: "+ choose app"
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
            HorizontalDivider(color = Line.copy(alpha = .22f))
        }
        Spacer(Modifier.height(19.dp))
        SectionTitle("02 / APPEARANCE")
        SettingRow("GLASS PANELS", glass, onGlass)
        SettingRow("QUIET ANIMATIONS", quiet, onQuiet)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onWallpaper) { Text("CHOOSE WALLPAPER", color = Orange) }
            if (hasWallpaper) TextButton(onClick = onClearWallpaper) { Text("REMOVE", color = Subtle) }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("03 / DIGITAL DETOX")
        SettingRow("HIDE FAVORITES IN A-Z", hideFavorites, onHideFavorites)
        Spacer(Modifier.height(13.dp))
        SectionTitle("04 / SYSTEM")
        TextButton(onClick = onSetHome) { Text("SET AS DEFAULT HOME", color = Orange) }
        Text("To switch back, open Android Settings → Default apps → Home app.",
            color = Subtle, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(15.dp))
        Text("orangre piano · personal build 0.1.0", fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, color = Subtle.copy(alpha = .65f))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(label, color = Orange, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(47.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Cream, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
