package com.tk.toolkit.tv.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tk.toolkit.tv.launcher.data.WallpaperSource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tk.toolkit.tv.launcher.LauncherViewModel
import com.tk.toolkit.tv.launcher.data.AppActions
import com.tk.toolkit.tv.launcher.data.AppActions.startApp
import com.tk.toolkit.tv.launcher.data.LauncherApp
import com.tk.toolkit.tv.launcher.weather.WeatherLayer
import com.tk.toolkit.tv.ui.theme.Dimens
import com.tk.toolkit.tv.ui.theme.Motion
import com.tk.toolkit.tv.ui.theme.ToolkitColors
import kotlinx.coroutines.launch

private const val MORE_APPS_KEY = "more-apps"
private const val DOCK = "dock:"
private const val ALL = "all:"
private const val HERO_KEY = "hero"
private const val DOCK_KEY = "dock"
private const val TITLE_KEY = "title"

/**
 * The home screen: the clock over open space for the focused app's artwork, the favorites
 * dock along the bottom, and All Apps in a grid below it. The dock and the grid share one
 * column layout, so Down from a favorite lands on the tile right under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val dockState = rememberLazyListState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String) = focusRequesters.getOrPut(key) { FocusRequester() }

    var menuApp by remember { mutableStateOf<LauncherApp?>(null) }
    // Whether the menu was opened from All Apps (Move then reorders the grid, not the dock).
    var menuFromGrid by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showCustomize by remember { mutableStateOf(false) }
    var wallpaperCaption by remember { mutableStateOf<String?>(null) }
    val wallpaperOpaque = remember { mutableStateOf(false) }
    // "My images" needs photo access; ask when it's picked, and switch once it's granted.
    val photoPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val askForPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setWallpaperSource(WallpaperSource.Mine)
        else Toast.makeText(context, "Allow photo access to use your own images", Toast.LENGTH_LONG).show()
    }
    // Where focus is, for putting it back when that tile goes away. Only read in effects.
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
    // Not saved: every fresh start begins on the dock.
    var initialFocusPlaced by remember { mutableStateOf(false) }
    // Only the backdrop reads these, so focus changes don't recompose the screen.
    val backdropApp = remember { mutableStateOf<LauncherApp?>(null) }
    val inGrid = remember { mutableStateOf(false) }
    // Home view: nothing focused, only the dock over the artwork; All Apps is hidden until focus returns.
    var homeView by remember { mutableStateOf(false) }
    val peekAlpha = animateFloatAsState(if (homeView) 0f else 1f, Motion.fade, label = "peek")
    // In the home view the dock settles into the space All Apps peeks into.
    val dockDrop = animateFloatAsState(if (homeView) 1f else 0f, Motion.dockSlide, label = "dockDrop")
    val homeAnchor = remember { FocusRequester() }
    val opening = rememberLaunchTransition()
    fun openApp(app: LauncherApp, from: Rect?) {
        if (state.movingKey == null) opening.open(app, from) { context.startApp(viewModel.launchIntent(app)) }
    }
    // The favorite to return to from the home view.
    var lastDockKey by remember { mutableStateOf<String?>(null) }

    val dockKeys = state.favorites.map { DOCK + it.packageName }
    val gridKeys = state.apps.map { ALL + it.packageName } +
        if (state.hiddenApps.isNotEmpty()) listOf(MORE_APPS_KEY) else emptyList()

    // Sizes: the dock uses the grid's column width, and sits so "All Apps" just peeks in below it.
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val tileWidth = (screenWidth - Dimens.EdgePadding * 2 - Dimens.TileGap * (Dimens.Columns - 1)) / Dimens.Columns
    val dockHeight = Dimens.LiftRoom + tileWidth * 9f / 16f + Dimens.LabelHeight
    val heroHeight = configuration.screenHeightDp.dp - dockHeight - Dimens.AllAppsPeek

    val density = LocalDensity.current
    val pageSpec = remember(density, heroHeight) {
        with(density) {
            pageScrollSpec(
                state = gridState,
                heroKey = HERO_KEY,
                dockKey = DOCK_KEY,
                titleKey = TITLE_KEY,
                dockRestPx = (heroHeight + Dimens.RowGap).toPx(),
                titleTopPx = 20.dp.toPx(),
                // Room above a focused row for "All Apps" (on the first row) or the row before.
                leadingPx = 80.dp.toPx(),
                trailingPx = 40.dp.toPx(),
                animation = Motion.verticalScroll,
            )
        }
    }

    suspend fun focusTile(key: String) {
        if (key.startsWith(DOCK)) {
            val index = dockKeys.indexOf(key)
            if (dockState.layoutInfo.visibleItemsInfo.none { it.index == index }) dockState.scrollToItem(index)
            // Deep in the grid the dock isn't composed yet; once it is, focusing it glides the rest of the way.
            if (gridState.layoutInfo.visibleItemsInfo.none { it.key == DOCK_KEY }) gridState.animateScrollToItem(0)
        } else {
            // Row items come after the hero, dock and title.
            val index = gridKeys.indexOf(key) + 3
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) gridState.scrollToItem(index)
        }
        requesterFor(key).requestFocusWhenReady()
    }

    // This is the home screen: Back never leaves it. It cancels a move; from the dock it goes to the
    // home view; from anywhere else it returns to the dock.
    BackHandler {
        when {
            state.movingKey != null -> viewModel.endMove()
            focusedKey?.startsWith(DOCK) == true && !homeView -> runCatching { homeAnchor.requestFocus() }
            else -> (dockKeys + gridKeys).firstOrNull()?.let { scope.launch { focusTile(it) } }
        }
    }

    // Initial focus, and keeping focus on screen when the focused tile goes away
    // (hidden, uninstalled, or unpinned).
    LaunchedEffect(dockKeys, gridKeys) {
        val all = dockKeys + gridKeys
        if (!state.loaded || all.isEmpty()) return@LaunchedEffect
        if (!initialFocusPlaced) {
            // Every start opens on the home view: the wallpaper, with the dock resting below it.
            initialFocusPlaced = true
            homeAnchor.requestFocusWhenReady()
            return@LaunchedEffect
        }
        val target = when {
            focusedKey == null || focusedKey in all -> return@LaunchedEffect
            else -> {
                val section = if (focusedKey!!.startsWith(DOCK)) dockKeys else gridKeys
                section.getOrNull(focusedIndex.coerceAtMost(section.lastIndex)) ?: all.first()
            }
        }
        initialFocusPlaced = true
        focusTile(target)
    }

    fun onTileFocused(key: String, index: Int, app: LauncherApp?) {
        focusedKey = key
        focusedIndex = index
        inGrid.value = !key.startsWith(DOCK)
        homeView = false
        if (key.startsWith(DOCK)) lastDockKey = key
        if (app != null) backdropApp.value = app
    }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { opening.blocksInput }) {
        Backdrop(backdropApp, inGrid, covered = { wallpaperOpaque.value })
        if (state.wallpaperSource == WallpaperSource.Weather) {
            WeatherLayer(visible = homeView, onCaption = { wallpaperCaption = it }, opaque = wallpaperOpaque)
        } else {
            WallpaperLayer(
                source = state.wallpaperSource,
                visible = homeView,
                onShowing = { wallpaperCaption = it?.let { w -> "${w.title}  \u00b7  ${w.credit}" } },
                opaque = wallpaperOpaque,
            )
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(Dimens.Columns),
                state = gridState,
                contentPadding = PaddingValues(start = Dimens.EdgePadding, end = Dimens.EdgePadding, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.TileGap),
                verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = HERO_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    inGrid.value = false
                                    homeView = true
                                }
                            },
                    ) {
                        ClockHeader(
                            onOpenCustomize = { showCustomize = true },
                            onOpenAudioOutput = { AppActions.openAudioOutput(context) },
                            onOpenSettings = { AppActions.openSystemSettings(context) },
                            modifier = Modifier.padding(top = 32.dp),
                            caption = wallpaperCaption,
                            captionAlpha = { 1f - peekAlpha.value },
                        )
                        // The home view's invisible focus spot, between the dock and the header:
                        // Up from the dock lands here, Down returns to the favorite you came from.
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .focusRequester(homeAnchor)
                                .focusProperties {
                                    (lastDockKey ?: dockKeys.firstOrNull())?.let { down = requesterFor(it) }
                                }
                                .navigationSound()
                                .focusable(),
                        )
                    }
                }
                if (state.favorites.isNotEmpty()) {
                    item(key = DOCK_KEY, span = { GridItemSpan(maxLineSpan) }) {
                        Dock(
                            favorites = state.favorites,
                            dockState = dockState,
                            tileWidth = tileWidth,
                            movingKey = state.movingKey.takeIf { !state.movingInGrid },
                            upTarget = homeAnchor,
                            requesterFor = ::requesterFor,
                            onTileFocused = { index, app -> onTileFocused(DOCK + app.packageName, index, app) },
                            onLaunch = ::openApp,
                            onMenu = { app ->
                                if (state.movingKey == null) {
                                    menuApp = app
                                    menuFromGrid = false
                                }
                            },
                            onMoveBy = viewModel::moveBy,
                            onDrop = viewModel::endMove,
                            modifier = Modifier
                                .bleed(Dimens.EdgePadding)
                                .graphicsLayer { translationY = dockDrop.value * Dimens.AllAppsPeek.toPx() },
                        )
                    }
                }
                item(key = TITLE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "All Apps",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = ToolkitColors.OnSurface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 12.dp)
                            .graphicsLayer { alpha = peekAlpha.value },
                    )
                }
                gridItemsIndexed(state.apps, key = { _, app -> ALL + app.packageName }) { index, app ->
                    val key = ALL + app.packageName
                    val isMoving = state.movingInGrid && app.packageName == state.movingKey
                    AppTile(
                        app = app,
                        index = index,
                        isMoving = isMoving,
                        dimmed = state.movingKey != null && !isMoving,
                        focusRequester = requesterFor(key),
                        onFocused = { onTileFocused(key, index, app) },
                        onClick = { from -> openApp(app, from) },
                        onLongClick = {
                            if (state.movingKey == null) {
                                menuApp = app
                                menuFromGrid = true
                            }
                        },
                        onMoveBy = viewModel::moveBy,
                        onDrop = viewModel::endMove,
                        columns = Dimens.Columns,
                        // Tiles glide to their new places when the sort changes.
                        modifier = Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = Motion.reorder)
                            .graphicsLayer { alpha = peekAlpha.value },
                    )
                }
                if (state.hiddenApps.isNotEmpty()) {
                    item(key = MORE_APPS_KEY) {
                        MoreAppsTile(
                            count = state.hiddenApps.size,
                            dimmed = state.movingKey != null,
                            focusRequester = requesterFor(MORE_APPS_KEY),
                            onFocused = { onTileFocused(MORE_APPS_KEY, state.apps.size, null) },
                            onClick = { if (state.movingKey == null) showHidden = true },
                            modifier = Modifier.graphicsLayer { alpha = peekAlpha.value },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.movingKey != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            MoveHint(grid = state.movingInGrid)
        }

        LaunchOverlay(opening)
    }

    if (showCustomize) {
        CustomizePanel(
            wallpaper = state.wallpaperSource,
            sort = state.sortMode,
            onDismiss = { showCustomize = false },
            onWallpaper = { source ->
                val needsPermission = source == WallpaperSource.Mine &&
                    ContextCompat.checkSelfPermission(context, photoPermission) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) askForPhotos.launch(photoPermission) else viewModel.setWallpaperSource(source)
            },
            onSort = viewModel::setSortMode,
        )
    }

    if (showHidden && state.hiddenApps.isNotEmpty()) {
        HiddenAppsPanel(
            apps = state.hiddenApps,
            onDismiss = { showHidden = false },
            onLaunch = { app -> openApp(app, null) },
            onLongPress = { menuApp = it },
        )
    }

    menuApp?.let { app ->
        val isHidden = state.hiddenApps.any { it.packageName == app.packageName }
        val isFavorite = state.favorites.any { it.packageName == app.packageName }
        // The panel slides out first and then runs the action; onDismiss has already cleared menuApp.
        AppActionsPanel(
            app = app,
            isHidden = isHidden,
            isFavorite = isFavorite,
            moveDescription = if (menuFromGrid) "Reorder All Apps" else "Reorder your favorites",
            onDismiss = { menuApp = null },
            onMove = {
                // Favorites move in the dock unless the menu came from All Apps.
                if (menuFromGrid || isFavorite) viewModel.startMove(app.packageName, inGrid = menuFromGrid)
            },
            onToggleFavorite = { viewModel.setFavorite(app.packageName, !isFavorite) },
            onToggleHidden = { viewModel.setHidden(app.packageName, !isHidden) },
            onAppInfo = { AppActions.openAppInfo(context, app.packageName) },
            onForceStop = { AppActions.forceStop(context, app.packageName) },
            onUninstall = { AppActions.uninstall(context, app.packageName) },
        )
    }
}

/** The favorites row. Scrolls sideways when there are more favorites than columns. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    favorites: List<LauncherApp>,
    dockState: LazyListState,
    tileWidth: Dp,
    movingKey: String?,
    upTarget: FocusRequester,
    requesterFor: (String) -> FocusRequester,
    onTileFocused: (index: Int, app: LauncherApp) -> Unit,
    onLaunch: (LauncherApp, from: Rect?) -> Unit,
    onMenu: (LauncherApp) -> Unit,
    onMoveBy: (Int) -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dockSpec = remember(density) {
        with(density) {
            edgeScrollSpec(
                leadingPx = Dimens.EdgePadding.toPx(),
                trailingPx = (Dimens.EdgePadding + Dimens.DockPeek).toPx(),
                animation = Motion.horizontalScroll,
            )
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides dockSpec) {
        LazyRow(
            state = dockState,
            contentPadding = PaddingValues(start = Dimens.EdgePadding, end = Dimens.EdgePadding, top = Dimens.LiftRoom),
            horizontalArrangement = Arrangement.spacedBy(Dimens.TileGap),
            modifier = modifier,
        ) {
            itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                val isMoving = app.packageName == movingKey
                AppTile(
                    app = app,
                    index = index,
                    isMoving = isMoving,
                    dimmed = movingKey != null && !isMoving,
                    focusRequester = requesterFor(DOCK + app.packageName),
                    onFocused = { onTileFocused(index, app) },
                    onClick = { from -> onLaunch(app, from) },
                    onLongClick = { onMenu(app) },
                    onMoveBy = onMoveBy,
                    onDrop = onDrop,
                    // Up from the dock goes to the home view, not straight to the header buttons.
                    upTarget = upTarget,
                    modifier = Modifier
                        .width(tileWidth)
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            // Slide only while moving; other changes apply instantly.
                            placementSpec = if (movingKey != null) Motion.reorder else null,
                        ),
                )
            }
        }
    }
}

/** Lets a full-width grid item reach the screen edges past the grid's side padding. */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun MoveHint(grid: Boolean) {
    val shape = RoundedCornerShape(50)
    Text(
        if (grid) "Use the arrows to move  ·  Press OK to drop" else "Use left and right to move  ·  Press OK to drop",
        style = MaterialTheme.typography.bodyMedium,
        color = ToolkitColors.OnSurface,
        modifier = Modifier
            .background(ToolkitColors.SurfaceRaised.copy(alpha = 0.95f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    )
}
