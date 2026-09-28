package com.example.ui.screens.games

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.games.ludo.AutoDiceController
import com.example.core.games.ludo.DiceMode
import com.example.core.games.ludo.GridCoord
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoGameState
import com.example.core.games.ludo.LudoPlayer
import com.example.core.games.ludo.LudoRuleSet
import com.example.core.games.ludo.LudoSoundManager
import com.example.core.games.ludo.LudoToken
import com.example.core.games.ludo.LudoTopology
import com.example.core.games.ludo.LudoTurnState
import com.example.core.games.ludo.TokenState
import com.example.data.repository.LudoRepository
import com.example.data.repository.PreferencesRepository
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoScreen(
    ludoRepository: LudoRepository,
    preferencesRepository: PreferencesRepository? = null,
    onNavigateBack: () -> Unit = {}
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedSoundPref = preferencesRepository?.ludoGameSoundsEnabled?.collectAsState()?.value ?: true
    var soundEnabled by remember { mutableStateOf(savedSoundPref) }

    val soundManager = remember(context) {
        LudoSoundManager(context, isSoundEnabled = soundEnabled)
    }
    LaunchedEffect(soundEnabled) {
        soundManager.isSoundEnabled = soundEnabled
    }
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    var playerCount by remember { mutableIntStateOf(4) }
    var diceMode by remember { mutableStateOf(DiceMode.STANDARD_RANDOM) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    var autoMoveSingle by remember { mutableStateOf(true) }
    val autoDiceController = remember { AutoDiceController() }

    var engine by remember {
        mutableStateOf(
            LudoEngine(
                selectedPlayerCount = 4,
                ruleSet = LudoRuleSet(autoMoveSingleOption = autoMoveSingle),
                initialDiceMode = DiceMode.STANDARD_RANDOM
            )
        )
    }
    var gameState by remember { mutableStateOf(engine.getGameState()) }

    var isDiceRollingAnim by remember { mutableStateOf(false) }
    var displayDiceValue by remember { mutableIntStateOf(gameState.diceValue) }

    // Animated position override during token movement
    var animatedTokenPositions by remember { mutableStateOf<Map<Int, GridCoord>>(emptyMap()) }

    var showRulesDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showHamburgerMenu by remember { mutableStateOf(false) }

    // Saved Game Startup flows
    var showStartupOverlay by remember { mutableStateOf(false) }
    var hasSavedGame by remember { mutableStateOf(false) }
    var savedGameData by remember { mutableStateOf<com.example.core.database.entity.LudoGameEntity?>(null) }
    var showNewGameConfirmFromStartup by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Player deactivation/reactivation states
    var showDeactivateConfirmDialog by remember { mutableStateOf(false) }
    var showMinPlayersWarning by remember { mutableStateOf(false) }
    var playerToDeactivate by remember { mutableStateOf<LudoPlayer?>(null) }

    // Mid-game starting match overwrite flows
    var pendingPlayerCountToStart by remember { mutableIntStateOf(4) }
    var showOverwriteConfirmMidGame by remember { mutableStateOf(false) }

    fun triggerHaptic(heavy: Boolean = false) {
        if (!hapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val duration = if (heavy) 80L else 30L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(duration)
                }
            }
        } catch (_: Exception) {}
    }

    // Check saved game status on startup
    LaunchedEffect(Unit) {
        val saved = ludoRepository.getSavedGame()
        if (saved != null) {
            hasSavedGame = true
            savedGameData = saved
            showStartupOverlay = true
        }
    }

    fun startNewGame(newCount: Int, mode: DiceMode = diceMode) {
        playerCount = 4
        diceMode = DiceMode.STANDARD_RANDOM
        animatedTokenPositions = emptyMap()
        isDiceRollingAnim = false
        autoDiceController.clearAll()
        val newEngine = LudoEngine(
            selectedPlayerCount = 4,
            ruleSet = LudoRuleSet(autoMoveSingleOption = autoMoveSingle),
            initialDiceMode = DiceMode.STANDARD_RANDOM
        )
        engine = newEngine
        gameState = newEngine.getGameState()
        displayDiceValue = gameState.diceValue
        scope.launch {
            ludoRepository.saveGame(newEngine, gameState)
            hasSavedGame = true
        }
    }

    fun checkAndStartNewGame(newCount: Int) {
        if (hasSavedGame) {
            pendingPlayerCountToStart = newCount
            showOverwriteConfirmMidGame = true
        } else {
            startNewGame(newCount)
        }
    }

    fun requestDeactivatePlayer(player: LudoPlayer) {
        val activeCount = gameState.players.count { it.isActive }
        if (activeCount <= 2) {
            showMinPlayersWarning = true
        } else {
            playerToDeactivate = player
            showDeactivateConfirmDialog = true
        }
    }

    fun restorePlayer(playerIndex: Int) {
        val nextState = engine.setPlayerActive(playerIndex, true)
        gameState = nextState
        scope.launch {
            ludoRepository.saveGame(engine, nextState)
            hasSavedGame = true
        }
    }

    fun executeRoll() {
        autoDiceController.onManualRoll(gameState.rollOpportunityId)
        if (isDiceRollingAnim) return
        if (gameState.winnerPlayerIndex != null) return
        if (gameState.turnState != LudoTurnState.WAITING_FOR_ROLL) return

        triggerHaptic()
        soundManager.onDiceRoll()
        isDiceRollingAnim = true
        engine.setTurnState(LudoTurnState.ROLLING)

        scope.launch {
            // Dice roll visual animation: cycle numbers for 400ms
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 400) {
                displayDiceValue = (1..6).random()
                delay(50)
            }

            // Resolve smart dice roll
            val updatedState = engine.rollDice()
            displayDiceValue = updatedState.diceValue
            isDiceRollingAnim = false
            gameState = updatedState
            ludoRepository.saveGame(engine, updatedState)

            if (updatedState.diceValue == 6) {
                soundManager.onSix()
            }

            if (updatedState.turnState == LudoTurnState.TURN_COMPLETE) {
                delay(80)
                val nextState = engine.passTurnToNextPlayer()
                displayDiceValue = nextState.diceValue
                gameState = nextState
                ludoRepository.saveGame(engine, nextState)
            }

            // Auto-move single option if enabled
            if (autoMoveSingle && updatedState.legalMoves.size == 1 && updatedState.turnState == LudoTurnState.WAITING_FOR_TOKEN_SELECTION) {
                delay(250)
                val autoTokenId = updatedState.legalMoves.first().tokenId
                // Trigger auto token move
                val selectedMove = updatedState.legalMoves.first()
                engine.setTurnState(LudoTurnState.ANIMATING_MOVE)
                gameState = engine.getGameState()

                val pathCoords = selectedMove.pathStepCoords
                if (pathCoords.isNotEmpty()) {
                    val globalTokenId = selectedMove.playerIndex * 4 + autoTokenId
                    for (coord in pathCoords) {
                        animatedTokenPositions = animatedTokenPositions + (globalTokenId to coord)
                        soundManager.onTokenStep()
                        delay(110)
                    }
                }

                val afterMove = engine.makeMove(autoTokenId)
                val globalTokenId = selectedMove.playerIndex * 4 + autoTokenId
                animatedTokenPositions = animatedTokenPositions - globalTokenId

                if (selectedMove.capturesToken != null) {
                    soundManager.onCapture()
                    triggerHaptic(heavy = true)
                }
                if (selectedMove.targetState == TokenState.FINISHED) {
                    soundManager.onTokenHome()
                }

                if (afterMove.winnerPlayerIndex != null) {
                    soundManager.onVictory()
                    ludoRepository.clearSavedGame()
                    hasSavedGame = false
                } else {
                    ludoRepository.saveGame(engine, afterMove)
                }

                displayDiceValue = afterMove.diceValue
                gameState = afterMove
            }
        }
    }

    fun executeTokenMove(tokenId: Int) {
        if (isDiceRollingAnim) return
        if (gameState.turnState != LudoTurnState.WAITING_FOR_TOKEN_SELECTION) return

        val selectedMove = gameState.legalMoves.firstOrNull { it.tokenId == tokenId } ?: return

        // Set turnState to ANIMATING_MOVE to lock inputs
        engine.setTurnState(LudoTurnState.ANIMATING_MOVE)
        gameState = engine.getGameState()
        triggerHaptic()

        scope.launch {
            val pathCoords = selectedMove.pathStepCoords
            if (pathCoords.isNotEmpty()) {
                val globalTokenId = selectedMove.playerIndex * 4 + tokenId
                for (coord in pathCoords) {
                    animatedTokenPositions = animatedTokenPositions + (globalTokenId to coord)
                    soundManager.onTokenStep()
                    delay(110)
                }
            }

            val afterMove = engine.makeMove(tokenId)
            val globalTokenId = selectedMove.playerIndex * 4 + tokenId
            animatedTokenPositions = animatedTokenPositions - globalTokenId

            if (selectedMove.capturesToken != null) {
                soundManager.onCapture()
                triggerHaptic(heavy = true)
            }
            if (selectedMove.targetState == TokenState.FINISHED) {
                soundManager.onTokenHome()
            }

            if (afterMove.winnerPlayerIndex != null) {
                soundManager.onVictory()
                ludoRepository.clearSavedGame()
                hasSavedGame = false
            } else {
                ludoRepository.saveGame(engine, afterMove)
            }

            displayDiceValue = afterMove.diceValue
            gameState = afterMove
        }
    }

    // Optional Per-Player Auto Dice LaunchedEffect
    val isCurrentPlayerAuto = autoDiceController.isAutoDiceEnabled(gameState.currentTurnPlayerIndex)
    val isAutoRollEligible = autoDiceController.canAutoRoll(gameState, isDiceRollingAnim)

    LaunchedEffect(
        gameState.rollOpportunityId,
        gameState.currentTurnPlayerIndex,
        isCurrentPlayerAuto,
        gameState.turnState,
        isDiceRollingAnim
    ) {
        if (isAutoRollEligible) {
            autoDiceController.scheduleAutoRoll(
                scope = this,
                opportunityId = gameState.rollOpportunityId,
                playerIndex = gameState.currentTurnPlayerIndex,
                delayMs = 800L,
                getState = { gameState },
                isBusy = { isDiceRollingAnim },
                onRoll = { executeRoll() }
            )
        } else {
            autoDiceController.cancelPendingRoll()
        }
    }

    val currentPlayer = gameState.players.getOrNull(gameState.currentTurnPlayerIndex) ?: gameState.players[0]
    val currentPlayerColor = Color(currentPlayer.colorHex)

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // TOP ROW: Red (P0) and Green (P1)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val p0 = gameState.players[0]
                    if (p0.isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LudoInteractiveDice(
                                value = displayDiceValue,
                                color = Color(p0.colorHex),
                                isRolling = isDiceRollingAnim && gameState.currentTurnPlayerIndex == 0,
                                isActiveTurn = gameState.currentTurnPlayerIndex == 0 && gameState.winnerPlayerIndex == null,
                                isClickable = gameState.currentTurnPlayerIndex == 0 && gameState.turnState == LudoTurnState.WAITING_FOR_ROLL,
                                onClick = { executeRoll() },
                                playerIndex = 0,
                                size = 54.dp
                            )
                            if (autoDiceController.isAutoDiceEnabled(0)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(p0.colorHex).copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color(p0.colorHex))
                                ) {
                                    Text(
                                        "AUTO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(p0.colorHex),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(54.dp))
                    }

                    val p1 = gameState.players[1]
                    if (p1.isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (autoDiceController.isAutoDiceEnabled(1)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(p1.colorHex).copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color(p1.colorHex))
                                ) {
                                    Text(
                                        "AUTO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(p1.colorHex),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            LudoInteractiveDice(
                                value = displayDiceValue,
                                color = Color(p1.colorHex),
                                isRolling = isDiceRollingAnim && gameState.currentTurnPlayerIndex == 1,
                                isActiveTurn = gameState.currentTurnPlayerIndex == 1 && gameState.winnerPlayerIndex == null,
                                isClickable = gameState.currentTurnPlayerIndex == 1 && gameState.turnState == LudoTurnState.WAITING_FOR_ROLL,
                                onClick = { executeRoll() },
                                playerIndex = 1,
                                size = 54.dp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(54.dp))
                    }
                }

                // Central Classic Ludo Board (Canonical 4-Quadrant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(2.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                        .shadow(4.dp, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ClassicLudoBoardView(
                        gameState = gameState,
                        playerCount = 4,
                        animatedPositions = animatedTokenPositions,
                        onTokenClicked = { executeTokenMove(it) }
                    )
                }

                // BOTTOM ROW: Blue (P3) and Yellow (P2)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val p3 = gameState.players[3]
                    if (p3.isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LudoInteractiveDice(
                                value = displayDiceValue,
                                color = Color(p3.colorHex),
                                isRolling = isDiceRollingAnim && gameState.currentTurnPlayerIndex == 3,
                                isActiveTurn = gameState.currentTurnPlayerIndex == 3 && gameState.winnerPlayerIndex == null,
                                isClickable = gameState.currentTurnPlayerIndex == 3 && gameState.turnState == LudoTurnState.WAITING_FOR_ROLL,
                                onClick = { executeRoll() },
                                playerIndex = 3,
                                size = 54.dp
                            )
                            if (autoDiceController.isAutoDiceEnabled(3)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(p3.colorHex).copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color(p3.colorHex))
                                ) {
                                    Text(
                                        "AUTO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(p3.colorHex),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(54.dp))
                    }

                    val p2 = gameState.players[2]
                    if (p2.isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (autoDiceController.isAutoDiceEnabled(2)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(p2.colorHex).copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color(p2.colorHex))
                                ) {
                                    Text(
                                        "AUTO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (p2.colorHex == 0xFFFDD835) Color(0xFFE65100) else Color(p2.colorHex),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            LudoInteractiveDice(
                                value = displayDiceValue,
                                color = Color(p2.colorHex),
                                isRolling = isDiceRollingAnim && gameState.currentTurnPlayerIndex == 2,
                                isActiveTurn = gameState.currentTurnPlayerIndex == 2 && gameState.winnerPlayerIndex == null,
                                isClickable = gameState.currentTurnPlayerIndex == 2 && gameState.turnState == LudoTurnState.WAITING_FOR_ROLL,
                                onClick = { executeRoll() },
                                playerIndex = 2,
                                size = 54.dp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(54.dp))
                    }
                }
            }

            // Absolute Top persistent navigation: Back on Left, Settings on Right
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Settings Button
                IconButton(onClick = { showHamburgerMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings Menu",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Welcome & Resume Saved Game Startup Overlay
            if (showStartupOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Saved Ludo Match",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = BentoPrimary
                            )
                            Text(
                                text = "Would you like to resume your previous saved match or start a fresh game?",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val saved = savedGameData
                                    if (saved != null) {
                                        playerCount = saved.playerCount
                                        val restoredEngine = LudoEngine(
                                            selectedPlayerCount = saved.playerCount,
                                            ruleSet = LudoRuleSet(autoMoveSingleOption = autoMoveSingle),
                                            initialDiceMode = diceMode
                                        )
                                        restoredEngine.restoreFromSaved(
                                            savedTokensJson = saved.tokensJson,
                                            currentTurn = saved.currentTurnIndex,
                                            diceValue = saved.diceValue,
                                            hasRolled = saved.hasRolledDice,
                                            winner = saved.winnerIndex
                                        )
                                        engine = restoredEngine
                                        gameState = restoredEngine.getGameState()
                                        displayDiceValue = gameState.diceValue
                                    }
                                    showStartupOverlay = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue Saved Match", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    showNewGameConfirmFromStartup = true
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Start New Match", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    showDeleteConfirm = true
                                }
                            ) {
                                Text("Delete Saved Game", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Startup Overwrite Confirmation Dialog
            if (showNewGameConfirmFromStartup) {
                AlertDialog(
                    onDismissRequest = { showNewGameConfirmFromStartup = false },
                    title = { Text("Start New Game?", fontWeight = FontWeight.Bold) },
                    text = { Text("This will permanently overwrite and delete your current saved match. Are you sure you want to proceed?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    ludoRepository.clearSavedGame()
                                    hasSavedGame = false
                                    savedGameData = null
                                    startNewGame(4)
                                    showNewGameConfirmFromStartup = false
                                    showStartupOverlay = false
                                }
                            }
                        ) {
                            Text("Overwrite & Start", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNewGameConfirmFromStartup = false }) { Text("Cancel") }
                    }
                )
            }

            // Startup Delete Confirmation Dialog
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Saved Game?", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to delete the saved match? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    ludoRepository.clearSavedGame()
                                    hasSavedGame = false
                                    savedGameData = null
                                    showDeleteConfirm = false
                                    showStartupOverlay = false
                                    startNewGame(4)
                                }
                            }
                        ) {
                            Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            // Player Deactivation Confirmation Dialog
            if (showDeactivateConfirmDialog) {
                val player = playerToDeactivate
                if (player != null) {
                    AlertDialog(
                        onDismissRequest = { showDeactivateConfirmDialog = false },
                        title = { Text("Deactivate ${player.name}?", fontWeight = FontWeight.Bold) },
                        text = { Text("This will temporarily deactivate ${player.name}. Their tokens will remain on the board, but their turn will be skipped. You can restore them at any point.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val nextState = engine.setPlayerActive(player.index, false)
                                        gameState = nextState
                                        ludoRepository.saveGame(engine, nextState)
                                        showDeactivateConfirmDialog = false
                                    }
                                }
                            ) {
                                Text("Temporarily Remove", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeactivateConfirmDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }

            // Minimum Players Warning Dialog
            if (showMinPlayersWarning) {
                AlertDialog(
                    onDismissRequest = { showMinPlayersWarning = false },
                    title = { Text("Cannot Deactivate Player", fontWeight = FontWeight.Bold) },
                    text = { Text("A minimum of two active players is required to play Ludo. Please restore another player before removing this one.") },
                    confirmButton = {
                        TextButton(onClick = { showMinPlayersWarning = false }) { Text("OK") }
                    }
                )
            }

            // Mid-game starting match overwrite flows
            if (showOverwriteConfirmMidGame) {
                AlertDialog(
                    onDismissRequest = { showOverwriteConfirmMidGame = false },
                    title = { Text("Overwrite Saved Game?", fontWeight = FontWeight.Bold) },
                    text = { Text("You have a saved game in progress. Starting a new match will overwrite it. Proceed?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    ludoRepository.clearSavedGame()
                                    hasSavedGame = false
                                    savedGameData = null
                                    startNewGame(pendingPlayerCountToStart)
                                    showOverwriteConfirmMidGame = false
                                }
                            }
                        ) {
                            Text("Overwrite", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOverwriteConfirmMidGame = false }) { Text("Cancel") }
                    }
                )
            }

            // Winner Victory Celebration Overlay
            if (gameState.winnerPlayerIndex != null) {
                LudoVictoryOverlay(
                    gameState = gameState,
                    onPlayAgain = { checkAndStartNewGame(4) },
                    onNewMatch = { checkAndStartNewGame(4) }
                )
            }
        }
    }

    // Hamburger Options Menu Dialog
    if (showHamburgerMenu) {
        AlertDialog(
            onDismissRequest = { showHamburgerMenu = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ludo Options", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Sound & Haptic Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Game Sounds", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = {
                                    soundEnabled = it
                                    soundManager.isSoundEnabled = it
                                    preferencesRepository?.setLudoGameSoundsEnabled(it)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-Move Single Option", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Switch(
                                checked = autoMoveSingle,
                                onCheckedChange = { autoMoveSingle = it }
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

                    // Manage Players Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Manage Players:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        gameState.players.forEach { player ->
                            val playerColor = Color(player.colorHex)
                            val displayControlColor = if (player.colorHex == 0xFFFDD835) Color(0xFFE65100) else playerColor

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (player.isActive) playerColor else playerColor.copy(alpha = 0.35f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (player.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isAutoOn = autoDiceController.isAutoDiceEnabled(player.index)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAutoOn) displayControlColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isAutoOn) displayControlColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                autoDiceController.toggleAutoDice(player.index)
                                            }
                                            .testTag("auto_dice_toggle_${player.index}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isAutoOn) displayControlColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                            )
                                            Text(
                                                text = if (isAutoOn) "AUTO ON" else "AUTO OFF",
                                                fontSize = 11.sp,
                                                fontWeight = if (isAutoOn) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAutoOn) displayControlColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    if (player.isActive) {
                                        TextButton(
                                            onClick = {
                                                requestDeactivatePlayer(player)
                                            }
                                        ) {
                                            Text("Remove", color = displayControlColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                restorePlayer(player.index)
                                            }
                                        ) {
                                            Text("Restore", color = displayControlColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

                    // Restart Button
                    Button(
                        onClick = {
                            showHamburgerMenu = false
                            showRestartDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Restart Match", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    
                    // Exit Button
                    OutlinedButton(
                        onClick = {
                            showHamburgerMenu = false
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Exit Game", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHamburgerMenu = false }) { Text("Close") }
            }
        )
    }

    // Rules & Settings Dialog
    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text("Ludo Rules & Sound Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select Dice Policy:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = diceMode == DiceMode.PIXELROX_SMART,
                            onClick = {
                                diceMode = DiceMode.PIXELROX_SMART
                                engine.setDiceMode(DiceMode.PIXELROX_SMART)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("PixelRox Smart Dice (Recommended)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Avoids useless zero-move rolls, enforces exact-home finishes, and caps maximum consecutive 6s at two.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = diceMode == DiceMode.STANDARD_RANDOM,
                            onClick = {
                                diceMode = DiceMode.STANDARD_RANDOM
                                engine.setDiceMode(DiceMode.STANDARD_RANDOM)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Standard Random", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Uniform 1-to-6 random dice generation (with two-consecutive 6s limit).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Game Sound Effects", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Audio for dice rolls, token steps & captures", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = {
                                soundEnabled = it
                                soundManager.isSoundEnabled = it
                                preferencesRepository?.setLudoGameSoundsEnabled(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-Move Single Option", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Automatically move when only 1 legal choice exists", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoMoveSingle,
                            onCheckedChange = { autoMoveSingle = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Manage Players:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BentoPrimary)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gameState.players.forEach { player ->
                            val playerColor = Color(player.colorHex)
                            val displayControlColor = if (player.colorHex == 0xFFFDD835) Color(0xFFE65100) else playerColor

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // LEFT: player color/name
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (player.isActive) playerColor else playerColor.copy(alpha = 0.35f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = player.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (player.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        if (!player.isActive) {
                                            Text(
                                                text = "Inactive",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                // RIGHT: action (AUTO toggle + Remove/Restore text buttons)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isAutoOn = autoDiceController.isAutoDiceEnabled(player.index)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAutoOn) displayControlColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isAutoOn) displayControlColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                autoDiceController.toggleAutoDice(player.index)
                                            }
                                            .testTag("rules_auto_dice_toggle_${player.index}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isAutoOn) displayControlColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                            )
                                            Text(
                                                text = if (isAutoOn) "AUTO ON" else "AUTO OFF",
                                                fontSize = 11.sp,
                                                fontWeight = if (isAutoOn) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAutoOn) displayControlColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    if (player.isActive) {
                                        TextButton(
                                            onClick = {
                                                requestDeactivatePlayer(player)
                                            }
                                        ) {
                                            Text("Remove", color = displayControlColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                restorePlayer(player.index)
                                            }
                                        ) {
                                            Text("Restore", color = displayControlColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRulesDialog = false }) { Text("Save & Close") }
            }
        )
    }

    // Restart Confirmation Dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Match?") },
            text = { Text("This will return all tokens to home bases and reset scores.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        startNewGame(playerCount)
                        showRestartDialog = false
                    }
                ) {
                    Text("Restart", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LudoStatusBanner(
    gameState: LudoGameState,
    currentPlayer: LudoPlayer,
    currentPlayerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BentoBorderLavender),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(currentPlayerColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentPlayer.name}'s Turn",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = currentPlayerColor
                )
            }

            Text(
                text = gameState.statusMessage,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlayerCornerCard(
    player: LudoPlayer,
    tokens: List<LudoToken>,
    isActiveTurn: Boolean,
    isRolling: Boolean,
    diceValue: Int,
    turnState: LudoTurnState,
    onDiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerColor = Color(player.colorHex)
    val finishedCount = tokens.count { it.state == TokenState.FINISHED }

    val infiniteTransition = rememberInfiniteTransition(label = "turn_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = if (player.isActive && isActiveTurn) {
            BorderStroke(2.dp, playerColor.copy(alpha = glowAlpha))
        } else {
            BorderStroke(1.dp, BentoBorderLavender.copy(alpha = 0.3f))
        },
        colors = CardDefaults.cardColors(
            containerColor = if (!player.isActive) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            } else if (isActiveTurn) {
                playerColor.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (player.isActive) playerColor else playerColor.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = player.name.first().toString(),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black.copy(alpha = if (player.isActive) 1.0f else 0.4f),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = player.name,
                        fontWeight = if (isActiveTurn && player.isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (player.isActive) {
                            if (isActiveTurn) playerColor else MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        maxLines = 1
                    )
                    Text(
                        text = if (!player.isActive) "Inactive" else (if (finishedCount == 4) "WINNER" else "$finishedCount/4 Home"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!player.isActive) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else if (finishedCount == 4) {
                            EmeraldGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (player.isActive) {
                Spacer(modifier = Modifier.width(6.dp))

                // Dedicated Per-Player Dice
                LudoInteractiveDice(
                    value = diceValue,
                    color = playerColor,
                    isRolling = isRolling,
                    isActiveTurn = isActiveTurn,
                    isClickable = isActiveTurn && turnState == LudoTurnState.WAITING_FOR_ROLL,
                    onClick = onDiceClick,
                    playerIndex = player.index,
                    size = 38.dp
                )
            }
        }
    }
}

@Composable
private fun LudoInteractiveDice(
    value: Int,
    color: Color,
    isRolling: Boolean,
    isActiveTurn: Boolean,
    isClickable: Boolean,
    onClick: () -> Unit,
    playerIndex: Int = 0,
    size: Dp = 40.dp
) {
    val rotation by animateFloatAsState(
        targetValue = if (isRolling) 360f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(animation = tween(180, easing = LinearEasing), repeatMode = RepeatMode.Restart)
        } else {
            tween(180)
        },
        label = "dice_rotation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "active_dice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_pulse"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(if (isActiveTurn && isClickable) pulseScale else 1.0f)
            .rotate(rotation)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActiveTurn) Color(0xFF1E2633) else Color(0xFF121720).copy(alpha = 0.5f))
            .border(
                width = if (isActiveTurn) 2.dp else 1.dp,
                color = if (isActiveTurn) color else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .shadow(if (isActiveTurn) 6.dp else 0.dp, RoundedCornerShape(10.dp))
            .testTag("ludo_roll_dice_button")
            .testTag("ludo_roll_dice_button_$playerIndex")
            .clickable(enabled = isClickable) { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(if (!isActiveTurn) 0.85f else 1.0f)
        ) {
            val pipColor = if (isActiveTurn) color else Color.Gray.copy(alpha = 0.5f)
            val r = (size.toPx() * 0.085f).coerceAtLeast(3f)
            val w = this.size.width
            val h = this.size.height
            val left = w * 0.25f
            val midX = w * 0.5f
            val right = w * 0.75f
            val top = h * 0.25f
            val midY = h * 0.5f
            val bot = h * 0.75f

            when (value.coerceIn(1, 6)) {
                1 -> drawCircle(pipColor, r, Offset(midX, midY))
                2 -> {
                    drawCircle(pipColor, r, Offset(left, top))
                    drawCircle(pipColor, r, Offset(right, bot))
                }
                3 -> {
                    drawCircle(pipColor, r, Offset(left, top))
                    drawCircle(pipColor, r, Offset(midX, midY))
                    drawCircle(pipColor, r, Offset(right, bot))
                }
                4 -> {
                    drawCircle(pipColor, r, Offset(left, top))
                    drawCircle(pipColor, r, Offset(right, top))
                    drawCircle(pipColor, r, Offset(left, bot))
                    drawCircle(pipColor, r, Offset(right, bot))
                }
                5 -> {
                    drawCircle(pipColor, r, Offset(left, top))
                    drawCircle(pipColor, r, Offset(right, top))
                    drawCircle(pipColor, r, Offset(midX, midY))
                    drawCircle(pipColor, r, Offset(left, bot))
                    drawCircle(pipColor, r, Offset(right, bot))
                }
                6 -> {
                    drawCircle(pipColor, r, Offset(left, top))
                    drawCircle(pipColor, r, Offset(right, top))
                    drawCircle(pipColor, r, Offset(left, midY))
                    drawCircle(pipColor, r, Offset(right, midY))
                    drawCircle(pipColor, r, Offset(left, bot))
                    drawCircle(pipColor, r, Offset(right, bot))
                }
            }
        }
    }
}

/**
 * 15x15 CLASSIC LUDO BOARD VIEW
 */
@Composable
private fun ClassicLudoBoardView(
    gameState: LudoGameState,
    playerCount: Int,
    animatedPositions: Map<Int, GridCoord>,
    onTokenClicked: (tokenId: Int) -> Unit
) {
    val legalTokenIds = gameState.legalMoves.map { it.tokenId }.toSet()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boardSizePx = constraints.maxWidth.toFloat()
        val cellSizePx = boardSizePx / 15f

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawClassicLudoBoardGeometry(cellSizePx, playerCount)
        }

        // Overlay Tokens
        val tokensGrouped = gameState.tokens.distinctBy { it.playerIndex * 4 + it.id }.groupBy { token ->
            val globalId = token.playerIndex * 4 + token.id
            animatedPositions[globalId] ?: calculateGridCoordForToken(token, gameState.playerCount)
        }

        tokensGrouped.forEach { (gridCoord, tokensInCell) ->
            val primaryLegalTokenId = determinePrimaryLegalTokenInCell(tokensInCell, gameState, legalTokenIds)

            // Sort tokens ascending by priority so that inactive tokens are composed first (bottom)
            // and the top active/selectable token is composed last (topmost).
            val sortedTokens = tokensInCell.sortedWith(
                compareBy<LudoToken> { token ->
                    getStackedTokenPriority(token, gameState, legalTokenIds, primaryLegalTokenId)
                }.thenBy { token ->
                    token.playerIndex * 4 + token.id
                }
            )

            sortedTokens.forEachIndexed { index, token ->
                val isCurrentPlayer = gameState.currentTurnPlayerIndex == token.playerIndex
                val isLegal = isCurrentPlayer && legalTokenIds.contains(token.id) && gameState.turnState == LudoTurnState.WAITING_FOR_TOKEN_SELECTION
                val playerColor = Color(gameState.players[token.playerIndex].colorHex)
                val zIndex = getStackedTokenPriority(token, gameState, legalTokenIds, primaryLegalTokenId)

                val subOffset = calculateSubCellOffset(index, sortedTokens.size, cellSizePx)
                val pixelX = (gridCoord.x * cellSizePx) + subOffset.x
                val pixelY = (gridCoord.y * cellSizePx) + subOffset.y

                val scale = when (sortedTokens.size) {
                    1 -> 1.0f
                    2 -> 0.75f
                    3 -> 0.65f
                    else -> 0.60f
                }

                TokenPieceComposable(
                    pixelPos = Offset(pixelX, pixelY),
                    cellSize = cellSizePx,
                    color = playerColor,
                    isLegal = isLegal,
                    scale = scale,
                    zIndex = zIndex,
                    testTag = "ludo_token_${token.playerIndex}_${token.id}",
                    onClick = { if (isLegal) onTokenClicked(token.id) }
                )
            }
        }
    }
}

private fun DrawScope.drawClassicLudoBoardGeometry(cellSize: Float, playerCount: Int) {
    val redColor = Color(0xFFEA2127)
    val greenColor = Color(0xFF0F9D58)
    val yellowColor = Color(0xFFFFCC00)
    val blueColor = Color(0xFF1976D2)
    val trackBgColor = Color.White
    val gridLineColor = Color(0xFFCCCCCC)

    // 1. Red Base (Top-Left)
    drawRect(redColor, Offset(0f, 0f), Size(6 * cellSize, 6 * cellSize))
    drawRect(gridLineColor, Offset(0f, 0f), Size(6 * cellSize, 6 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))
    drawRect(Color.White, Offset(cellSize * 1f, cellSize * 1f), Size(4 * cellSize, 4 * cellSize))
    drawRect(gridLineColor, Offset(cellSize * 1f, cellSize * 1f), Size(4 * cellSize, 4 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))

    // 2. Green Base (Top-Right)
    val gAlpha = if (playerCount == 2) 0.15f else 1.0f
    drawRect(greenColor.copy(alpha = gAlpha), Offset(9 * cellSize, 0f), Size(6 * cellSize, 6 * cellSize))
    drawRect(gridLineColor, Offset(9 * cellSize, 0f), Size(6 * cellSize, 6 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))
    drawRect(Color.White, Offset(cellSize * 10f, cellSize * 1f), Size(4 * cellSize, 4 * cellSize))
    drawRect(gridLineColor, Offset(cellSize * 10f, cellSize * 1f), Size(4 * cellSize, 4 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))

    // 3. Yellow Base (Bottom-Right)
    drawRect(yellowColor, Offset(9 * cellSize, 9 * cellSize), Size(6 * cellSize, 6 * cellSize))
    drawRect(gridLineColor, Offset(9 * cellSize, 9 * cellSize), Size(6 * cellSize, 6 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))
    drawRect(Color.White, Offset(cellSize * 10f, cellSize * 10f), Size(4 * cellSize, 4 * cellSize))
    drawRect(gridLineColor, Offset(cellSize * 10f, cellSize * 10f), Size(4 * cellSize, 4 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))

    // 4. Blue Base (Bottom-Left)
    val bAlpha = if (playerCount == 4) 1.0f else 0.15f
    drawRect(blueColor.copy(alpha = bAlpha), Offset(0f, 9 * cellSize), Size(6 * cellSize, 6 * cellSize))
    drawRect(gridLineColor, Offset(0f, 9 * cellSize), Size(6 * cellSize, 6 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))
    drawRect(Color.White, Offset(cellSize * 1f, cellSize * 10f), Size(4 * cellSize, 4 * cellSize))
    drawRect(gridLineColor, Offset(cellSize * 1f, cellSize * 10f), Size(4 * cellSize, 4 * cellSize), style = Stroke(1.5f * 1.dp.toPx()))

    // 5. Track Cross Cells
    for (x in 6..8) {
        for (y in 0 until 15) {
            if (!(x in 6..8 && y in 6..8)) {
                drawRect(trackBgColor, Offset(x * cellSize, y * cellSize), Size(cellSize, cellSize))
                drawRect(gridLineColor, Offset(x * cellSize, y * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
            }
        }
    }
    for (y in 6..8) {
        for (x in 0 until 15) {
            if (!(x in 6..8 && y in 6..8)) {
                drawRect(trackBgColor, Offset(x * cellSize, y * cellSize), Size(cellSize, cellSize))
                drawRect(gridLineColor, Offset(x * cellSize, y * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
            }
        }
    }

    // 6. Colored Home Lanes
    for (x in 1..5) {
        drawRect(redColor, Offset(x * cellSize, 7 * cellSize), Size(cellSize, cellSize))
        drawRect(gridLineColor, Offset(x * cellSize, 7 * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
    }
    if (playerCount > 2) {
        for (y in 1..5) {
            drawRect(greenColor, Offset(7 * cellSize, y * cellSize), Size(cellSize, cellSize))
            drawRect(gridLineColor, Offset(7 * cellSize, y * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
        }
    }
    for (x in 9..13) {
        drawRect(yellowColor, Offset(x * cellSize, 7 * cellSize), Size(cellSize, cellSize))
        drawRect(gridLineColor, Offset(x * cellSize, 7 * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
    }
    if (playerCount == 4) {
        for (y in 9..13) {
            drawRect(blueColor, Offset(7 * cellSize, y * cellSize), Size(cellSize, cellSize))
            drawRect(gridLineColor, Offset(7 * cellSize, y * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
        }
    }

    // 7. Starting cells
    drawRect(redColor, Offset(1 * cellSize, 6 * cellSize), Size(cellSize, cellSize))
    drawRect(gridLineColor, Offset(1 * cellSize, 6 * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
    if (playerCount > 2) {
        drawRect(greenColor, Offset(8 * cellSize, 1 * cellSize), Size(cellSize, cellSize))
        drawRect(gridLineColor, Offset(8 * cellSize, 1 * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
    }
    drawRect(yellowColor, Offset(13 * cellSize, 8 * cellSize), Size(cellSize, cellSize))
    drawRect(gridLineColor, Offset(13 * cellSize, 8 * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
    if (playerCount == 4) {
        drawRect(blueColor, Offset(6 * cellSize, 13 * cellSize), Size(cellSize, cellSize))
        drawRect(gridLineColor, Offset(6 * cellSize, 13 * cellSize), Size(cellSize, cellSize), style = Stroke(1.dp.toPx()))
    }

    // 8. Track Flow Arrows (Markings)
    drawFlowArrow(0f, 6f, "RIGHT", redColor, cellSize)
    drawFlowArrow(8f, 0f, "DOWN", greenColor, cellSize)
    drawFlowArrow(14f, 8f, "LEFT", yellowColor, cellSize)
    drawFlowArrow(6f, 14f, "UP", blueColor, cellSize)

    // 9. Safe Stars
    val safeStarCoords = listOf(
        Offset(6.5f * cellSize, 2.5f * cellSize),
        Offset(12.5f * cellSize, 6.5f * cellSize),
        Offset(8.5f * cellSize, 12.5f * cellSize),
        Offset(2.5f * cellSize, 8.5f * cellSize),
        Offset(1.5f * cellSize, 6.5f * cellSize),
        Offset(8.5f * cellSize, 1.5f * cellSize),
        Offset(13.5f * cellSize, 8.5f * cellSize),
        Offset(6.5f * cellSize, 13.5f * cellSize)
    )

    for (starCenter in safeStarCoords) {
        drawStarSymbol(starCenter, radius = cellSize * 0.35f, color = Color(0xFF555555))
    }

    // 10. Center Home Goal Triangles
    val centerPx = Offset(7.5f * cellSize, 7.5f * cellSize)

    val redTri = Path().apply {
        moveTo(6 * cellSize, 6 * cellSize)
        lineTo(centerPx.x, centerPx.y)
        lineTo(6 * cellSize, 9 * cellSize)
        close()
    }
    drawPath(redTri, redColor)

    val greenTri = Path().apply {
        moveTo(6 * cellSize, 6 * cellSize)
        lineTo(9 * cellSize, 6 * cellSize)
        lineTo(centerPx.x, centerPx.y)
        close()
    }
    drawPath(greenTri, if (playerCount > 2) greenColor else greenColor.copy(alpha = 0.2f))

    val yellowTri = Path().apply {
        moveTo(9 * cellSize, 6 * cellSize)
        lineTo(9 * cellSize, 9 * cellSize)
        lineTo(centerPx.x, centerPx.y)
        close()
    }
    drawPath(yellowTri, yellowColor)

    val blueTri = Path().apply {
        moveTo(6 * cellSize, 9 * cellSize)
        lineTo(centerPx.x, centerPx.y)
        lineTo(9 * cellSize, 9 * cellSize)
        close()
    }
    drawPath(blueTri, if (playerCount == 4) blueColor else blueColor.copy(alpha = 0.2f))

    // Triangles divider outlines
    drawPath(redTri, gridLineColor, style = Stroke(1.5f * 1.dp.toPx()))
    drawPath(greenTri, gridLineColor, style = Stroke(1.5f * 1.dp.toPx()))
    drawPath(yellowTri, gridLineColor, style = Stroke(1.5f * 1.dp.toPx()))
    drawPath(blueTri, gridLineColor, style = Stroke(1.5f * 1.dp.toPx()))

    drawRect(gridLineColor, Offset(6 * cellSize, 6 * cellSize), Size(3 * cellSize, 3 * cellSize), style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawFlowArrow(gridX: Float, gridY: Float, direction: String, color: Color, cellSize: Float) {
    val cx = (gridX + 0.5f) * cellSize
    val cy = (gridY + 0.5f) * cellSize
    val size = cellSize * 0.45f
    val path = Path()
    when (direction) {
        "RIGHT" -> {
            path.moveTo(cx - size * 0.5f, cy - size * 0.2f)
            path.lineTo(cx + size * 0.1f, cy - size * 0.2f)
            path.lineTo(cx + size * 0.1f, cy - size * 0.45f)
            path.lineTo(cx + size * 0.6f, cy)
            path.lineTo(cx + size * 0.1f, cy + size * 0.45f)
            path.lineTo(cx + size * 0.1f, cy + size * 0.2f)
            path.lineTo(cx - size * 0.5f, cy + size * 0.2f)
            path.close()
        }
        "DOWN" -> {
            path.moveTo(cx - size * 0.2f, cy - size * 0.5f)
            path.lineTo(cx - size * 0.2f, cy + size * 0.1f)
            path.lineTo(cx - size * 0.45f, cy + size * 0.1f)
            path.lineTo(cx, cy + size * 0.6f)
            path.lineTo(cx + size * 0.45f, cy + size * 0.1f)
            path.lineTo(cx + size * 0.2f, cy + size * 0.1f)
            path.lineTo(cx + size * 0.2f, cy - size * 0.5f)
            path.close()
        }
        "LEFT" -> {
            path.moveTo(cx + size * 0.5f, cy - size * 0.2f)
            path.lineTo(cx - size * 0.1f, cy - size * 0.2f)
            path.lineTo(cx - size * 0.1f, cy - size * 0.45f)
            path.lineTo(cx - size * 0.6f, cy)
            path.lineTo(cx - size * 0.1f, cy + size * 0.45f)
            path.lineTo(cx - size * 0.1f, cy + size * 0.2f)
            path.lineTo(cx + size * 0.5f, cy + size * 0.2f)
            path.close()
        }
        "UP" -> {
            path.moveTo(cx - size * 0.2f, cy + size * 0.5f)
            path.lineTo(cx - size * 0.2f, cy - size * 0.1f)
            path.lineTo(cx - size * 0.45f, cy - size * 0.1f)
            path.lineTo(cx, cy - size * 0.6f)
            path.lineTo(cx + size * 0.45f, cy - size * 0.1f)
            path.lineTo(cx + size * 0.2f, cy - size * 0.1f)
            path.lineTo(cx + size * 0.2f, cy + size * 0.5f)
            path.close()
        }
    }
    drawPath(path, color.copy(alpha = 0.25f))
    drawPath(path, Color(0xFF666666).copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawStarSymbol(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val points = 5
    val innerRadius = radius * 0.45f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = Math.toRadians((i * 36.0) - 90.0)
        val x = center.x + (r * cos(angle).toFloat())
        val y = center.y + (r * sin(angle).toFloat())
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, Color(0xFF555555), style = Stroke(1.5f * 1.dp.toPx()))
}

private fun calculateGridCoordForToken(token: LudoToken, playerCount: Int): GridCoord {
    val topology = LudoTopology.getTopology(playerCount)
    return when (token.state) {
        TokenState.IN_HOME_YARD -> {
            val slots = topology.baseSlotGridCoords.getOrNull(token.playerIndex) ?: topology.baseSlotGridCoords[0]
            slots.getOrNull(token.id) ?: GridCoord(1.5f, 1.5f)
        }
        TokenState.ON_TRACK -> {
            val pos = token.trackPosition.coerceIn(0, topology.totalTrackCells - 1)
            topology.trackGridCoords.getOrNull(pos) ?: GridCoord(1f, 6f)
        }
        TokenState.IN_HOME_STRETCH -> {
            val lane = topology.homeStretchGridCoords.getOrNull(token.playerIndex) ?: topology.homeStretchGridCoords[0]
            lane.getOrNull(token.homeStretchPosition.coerceIn(0, 4)) ?: GridCoord(7.5f, 7.5f)
        }
        TokenState.FINISHED -> {
            topology.homeGoalGridCoords.getOrNull(token.playerIndex) ?: GridCoord(7.5f, 7.5f)
        }
    }
}

private fun calculateSubCellOffset(index: Int, totalInCell: Int, cellSize: Float): Offset {
    if (totalInCell <= 1) return Offset(0f, 0f)
    val spreadX = cellSize * 0.18f
    val spreadY = cellSize * 0.18f
    return when (totalInCell) {
        2 -> {
            when (index) {
                0 -> Offset(-spreadX, 0f)
                1 -> Offset(spreadX, 0f)
                else -> Offset(0f, 0f)
            }
        }
        3 -> {
            when (index) {
                0 -> Offset(0f, -spreadY)
                1 -> Offset(-spreadX, spreadY)
                2 -> Offset(spreadX, spreadY)
                else -> Offset(0f, 0f)
            }
        }
        else -> { // 4 or more
            when (index % 4) {
                0 -> Offset(-spreadX, -spreadY)
                1 -> Offset(spreadX, -spreadY)
                2 -> Offset(-spreadX, spreadY)
                3 -> Offset(spreadX, spreadY)
                else -> Offset(0f, 0f)
            }
        }
    }
}

private fun Color.darken(factor: Float = 0.6f): Color {
    return Color(
        red = (this.red * factor).coerceIn(0f, 1f),
        green = (this.green * factor).coerceIn(0f, 1f),
        blue = (this.blue * factor).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

private fun Color.lighten(factor: Float = 0.5f): Color {
    return Color(
        red = (this.red + (1f - this.red) * factor).coerceIn(0f, 1f),
        green = (this.green + (1f - this.green) * factor).coerceIn(0f, 1f),
        blue = (this.blue + (1f - this.blue) * factor).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

/**
 * Determines which token in this cell is the designated primary legal token.
 * If multiple same-player tokens in this cell are legal moves, this provides
 * deterministic priority (explicitly selected token first, then first in legalMoves).
 */
internal fun determinePrimaryLegalTokenInCell(
    tokensInCell: List<LudoToken>,
    gameState: LudoGameState,
    legalTokenIds: Set<Int>
): Int? {
    val legalInThisCell = tokensInCell.filter { token ->
        val isCurrentPlayer = gameState.currentTurnPlayerIndex == token.playerIndex
        isCurrentPlayer && legalTokenIds.contains(token.id) && gameState.turnState == LudoTurnState.WAITING_FOR_TOKEN_SELECTION
    }
    if (legalInThisCell.isEmpty()) return null

    return if (gameState.selectedTokenId != null && legalInThisCell.any { it.id == gameState.selectedTokenId }) {
        gameState.selectedTokenId
    } else {
        gameState.legalMoves.firstOrNull { move -> legalInThisCell.any { it.id == move.tokenId } }?.tokenId
            ?: legalInThisCell.firstOrNull()?.id
    }
}

/**
 * Computes deterministic priority for a token when rendering stacked tokens in a cell.
 * Priority tiers:
 * - Tier 0: Inactive / non-selectable tokens: [1.0f .. 9.0f]
 * - Tier 1: Selectable tokens: [100.0f .. 199.0f]
 * - Tier 2: Top / currently active selected token in this cell: [200.0f .. 299.0f]
 */
internal fun getStackedTokenPriority(
    token: LudoToken,
    gameState: LudoGameState,
    legalTokenIds: Set<Int>,
    primaryLegalTokenIdInCell: Int?
): Float {
    val isCurrentPlayer = gameState.currentTurnPlayerIndex == token.playerIndex
    val isLegal = isCurrentPlayer &&
            legalTokenIds.contains(token.id) &&
            gameState.turnState == LudoTurnState.WAITING_FOR_TOKEN_SELECTION

    if (!isLegal) {
        // Tier 0: Inactive tokens render at bottom tier (stable order by player/token ID)
        return 1.0f + (token.playerIndex * 4 + token.id) * 0.05f
    }

    // Is it the currently selected token, or the primary legal token in this cell?
    val isTopActive = (gameState.selectedTokenId != null && gameState.selectedTokenId == token.id) ||
            (gameState.selectedTokenId == null && primaryLegalTokenIdInCell == token.id)

    return if (isTopActive) {
        // Tier 2: Top / active selectable token
        200.0f + (token.playerIndex * 4 + token.id) * 0.05f
    } else {
        // Tier 1: Other selectable tokens in this cell
        100.0f + (token.playerIndex * 4 + token.id) * 0.05f
    }
}

@Composable
private fun TokenPieceComposable(
    pixelPos: Offset,
    cellSize: Float,
    color: Color,
    isLegal: Boolean,
    scale: Float = 1.0f,
    zIndex: Float = 0f,
    testTag: String = "",
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val sizeDp = with(density) { (cellSize * 1.35f * scale).toDp() }

    val infiniteTransition = rememberInfiniteTransition(label = "indicator_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indicator_scale"
    )

    val xDp = with(density) { (pixelPos.x + (cellSize * 0.5f)).toDp() - (sizeDp / 2) }
    val yDp = with(density) { (pixelPos.y + (cellSize * 0.5f)).toDp() - (sizeDp / 2) }

    val baseModifier = Modifier
        .offset(x = xDp, y = yDp)
        .size(sizeDp)
        .zIndex(zIndex)
        .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)

    // CRITICAL HITBOX RULE:
    // Only currently selectable tokens are clickable.
    // Inactive tokens must NEVER have a clickable modifier to prevent intercepting touches intended for selectable tokens.
    val finalModifier = if (isLegal) {
        baseModifier.clickable { onClick() }
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val darkColor = color.darken(0.55f)
            val lightColor = color.lighten(0.45f)
            val outlineColor = Color(0xFF111111)
            val outlineStroke = 1.5f * density.density

            // 1. MOVABLE CIRCULAR INDICATOR (UNDERNEATH everything, if legal)
            if (isLegal) {
                val pulseRadius = w * 0.44f * pulseScale
                // Large solid colored circle using the player's exact color
                drawCircle(
                    color = color,
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = pulseRadius
                )
                // A clean white circular rim around the solid indicator circle
                drawCircle(
                    color = Color.White,
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = pulseRadius,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Outer dark subtle outline for separation
                drawCircle(
                    color = Color(0xFF111111).copy(alpha = 0.5f),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = pulseRadius + 1.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 2. PAWN GROUND SHADOW (ground projection under base)
            drawOval(
                color = Color.Black.copy(alpha = 0.3f),
                topLeft = Offset(w * 0.20f, h * 0.76f),
                size = Size(w * 0.60f, h * 0.10f)
            )

            // Define the custom bezier path for the tapered teardrop/pin-shaped body
            val bodyPath = Path().apply {
                // Neck left (tapered point)
                moveTo(w * 0.40f, h * 0.42f)
                // Left shoulder curve flaring out
                cubicTo(
                    w * 0.35f, h * 0.48f,
                    w * 0.20f, h * 0.62f,
                    w * 0.20f, h * 0.72f
                )
                // Rounded bottom base
                quadraticTo(
                    w * 0.50f, h * 0.81f,
                    w * 0.80f, h * 0.72f
                )
                // Right shoulder curve curving in
                cubicTo(
                    w * 0.80f, h * 0.62f,
                    w * 0.65f, h * 0.48f,
                    w * 0.60f, h * 0.42f
                )
                close()
            }

            // 3. OUTLINE PASS FOR BODY
            drawPath(
                path = bodyPath,
                color = outlineColor,
                style = Stroke(width = outlineStroke * 2)
            )

            // 4. FILL BODY
            drawPath(
                path = bodyPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(darkColor, color, lightColor, color, darkColor),
                    startX = w * 0.20f,
                    endX = w * 0.80f
                )
            )

            // 5. BASE PLATE RENDER
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(lightColor, darkColor),
                    startY = h * 0.70f,
                    endY = h * 0.78f
                ),
                topLeft = Offset(w * 0.20f, h * 0.70f),
                size = Size(w * 0.60f, h * 0.08f)
            )
            drawOval(
                color = outlineColor,
                topLeft = Offset(w * 0.20f, h * 0.70f),
                size = Size(w * 0.60f, h * 0.08f),
                style = Stroke(width = outlineStroke)
            )

            // 6. COLLAR/NECK RING
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.LightGray),
                    startY = h * 0.40f,
                    endY = h * 0.44f
                ),
                topLeft = Offset(w * 0.38f, h * 0.40f),
                size = Size(w * 0.24f, h * 0.04f)
            )
            drawOval(
                color = outlineColor,
                topLeft = Offset(w * 0.38f, h * 0.40f),
                size = Size(w * 0.24f, h * 0.04f),
                style = Stroke(width = outlineStroke)
            )

            // 7. GLOSSY SPHERICAL HEAD WITH WHITE RIM
            val headCenter = Offset(w * 0.5f, h * 0.24f)
            val rimRadius = w * 0.21f
            val innerHeadRadius = w * 0.165f

            // White rim
            drawCircle(
                color = Color.White,
                center = headCenter,
                radius = rimRadius
            )
            drawCircle(
                color = outlineColor,
                center = headCenter,
                radius = rimRadius,
                style = Stroke(width = outlineStroke)
            )

            // Glossy inner colored head
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lightColor, color, darkColor),
                    center = Offset(w * 0.45f, h * 0.19f),
                    radius = innerHeadRadius * 1.3f
                ),
                center = headCenter,
                radius = innerHeadRadius
            )
            drawCircle(
                color = outlineColor.copy(alpha = 0.5f),
                center = headCenter,
                radius = innerHeadRadius,
                style = Stroke(width = 1.dp.toPx())
            )

            // Dual opacity glossy highlight glare
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                center = Offset(w * 0.45f, h * 0.19f),
                radius = innerHeadRadius * 0.25f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                center = Offset(w * 0.45f, h * 0.19f),
                radius = innerHeadRadius * 0.5f
            )
        }
    }
}

/**
 * THREE PLAYER LUDO BOARD
 */
@Composable
private fun ThreePlayerLudoBoardView(
    gameState: LudoGameState,
    animatedPositions: Map<Int, GridCoord>,
    displayDiceValue: Int,
    isDiceRolling: Boolean,
    onDiceClick: () -> Unit,
    onTokenClicked: (tokenId: Int) -> Unit
) {
    val legalTokenIds = gameState.legalMoves.map { it.tokenId }.toSet()
    val density = LocalDensity.current
    val topology = LudoTopology.getTopology(3)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boardSizePx = min(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val cellSizePx = boardSizePx / 15f

        Canvas(modifier = Modifier.size(with(density) { boardSizePx.toDp() })) {
            // Background
            drawRect(Color(0xFF0F141C), size = Size(boardSizePx, boardSizePx))

            // 1. Draw 3-way center finish triangles
            val centerPx = Offset(7.5f * cellSizePx, 7.5f * cellSizePx)
            for (p in 0 until 3) {
                val playerColor = Color(gameState.players[p].colorHex)
                val a1 = Math.toRadians(p * 120.0 - 150.0)
                val a2 = Math.toRadians(p * 120.0 - 30.0)
                val rDist = 1.3f * cellSizePx
                val p1 = Offset(centerPx.x + rDist * cos(a1).toFloat(), centerPx.y + rDist * sin(a1).toFloat())
                val p2 = Offset(centerPx.x + rDist * cos(a2).toFloat(), centerPx.y + rDist * sin(a2).toFloat())

                val path = Path().apply {
                    moveTo(centerPx.x, centerPx.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    close()
                }
                drawPath(path, playerColor)
                drawPath(path, Color(0xFF283444), style = Stroke(2.dp.toPx()))
            }

            // 2. Draw Track Cells
            val safeCells = topology.safeCells
            val startCells = topology.playerStarts
            val trackCellDrawnSize = cellSizePx * 0.82f
            val trackCellMarginOffset = cellSizePx * 0.09f

            topology.trackGridCoords.forEachIndexed { i, coord ->
                val isSafe = safeCells.contains(i)
                val isStart = startCells.contains(i)
                val cellColor = when {
                    isStart -> {
                        val pIdx = startCells.indexOf(i)
                        Color(gameState.players.getOrNull(pIdx)?.colorHex ?: 0xFFFFFFFF).copy(alpha = 0.85f)
                    }
                    else -> Color(0xFF161C24)
                }

                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(coord.x * cellSizePx + trackCellMarginOffset + 1.dp.toPx(), coord.y * cellSizePx + trackCellMarginOffset + 1.dp.toPx()),
                    size = Size(trackCellDrawnSize - 2.dp.toPx(), trackCellDrawnSize - 2.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF283444),
                    topLeft = Offset(coord.x * cellSizePx + trackCellMarginOffset, coord.y * cellSizePx + trackCellMarginOffset),
                    size = Size(trackCellDrawnSize, trackCellDrawnSize),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )

                if (isSafe) {
                    val cellCenter = Offset((coord.x + 0.5f) * cellSizePx, (coord.y + 0.5f) * cellSizePx)
                    drawStarSymbol(cellCenter, radius = cellSizePx * 0.35f, color = Color(0xFFFFD700))
                }
            }

            // 3. Draw Home Lanes
            for (p in 0 until 3) {
                val playerColor = Color(gameState.players[p].colorHex)
                val lane = topology.homeStretchGridCoords.getOrNull(p) ?: emptyList()
                val laneCellDrawnSize = cellSizePx * 0.82f
                val laneCellMarginOffset = cellSizePx * 0.09f
                for (coord in lane) {
                    drawRoundRect(
                        color = playerColor,
                        topLeft = Offset(coord.x * cellSizePx + laneCellMarginOffset + 1.dp.toPx(), coord.y * cellSizePx + laneCellMarginOffset + 1.dp.toPx()),
                        size = Size(laneCellDrawnSize - 2.dp.toPx(), laneCellDrawnSize - 2.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFF283444),
                        topLeft = Offset(coord.x * cellSizePx + laneCellMarginOffset, coord.y * cellSizePx + laneCellMarginOffset),
                        size = Size(laneCellDrawnSize, laneCellDrawnSize),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                }
            }

            // 4. Draw Player Bases
            for (p in 0 until 3) {
                val playerColor = Color(gameState.players[p].colorHex)
                val angleDeg = p * 120f - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val dirX = cos(angleRad).toFloat()
                val dirY = sin(angleRad).toFloat()
                val perpX = -dirY
                val perpY = dirX

                val yardCenterX = 7.5f + 5.0f * dirX - 3.0f * perpX
                val yardCenterY = 7.5f + 5.0f * dirY - 3.0f * perpY

                drawRoundRect(
                    color = playerColor.copy(alpha = 0.22f),
                    topLeft = Offset((yardCenterX - 1.8f) * cellSizePx, (yardCenterY - 1.8f) * cellSizePx),
                    size = Size(3.6f * cellSizePx, 3.6f * cellSizePx),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
                drawRoundRect(
                    color = playerColor,
                    topLeft = Offset((yardCenterX - 1.8f) * cellSizePx, (yardCenterY - 1.8f) * cellSizePx),
                    size = Size(3.6f * cellSizePx, 3.6f * cellSizePx),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(3.dp.toPx())
                )

                drawRoundRect(
                    color = Color(0xFF0F141C),
                    topLeft = Offset((yardCenterX - 1.3f) * cellSizePx, (yardCenterY - 1.3f) * cellSizePx),
                    size = Size(2.6f * cellSizePx, 2.6f * cellSizePx),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                val slots = topology.baseSlotGridCoords.getOrNull(p) ?: emptyList()
                for (slot in slots) {
                    val slotCenter = Offset(slot.x * cellSizePx, slot.y * cellSizePx)
                    drawCircle(Color.White, radius = cellSizePx * 0.42f, center = slotCenter)
                    drawCircle(playerColor, radius = cellSizePx * 0.28f, center = slotCenter)
                }
            }
        }

        // 5. Overlay Player Name/Status Labels
        for (p in 0 until 3) {
            val player = gameState.players.getOrNull(p)
            if (player != null) {
                val angleDeg = p * 120f - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val dirX = cos(angleRad).toFloat()
                val dirY = sin(angleRad).toFloat()
                val perpX = -dirY
                val perpY = dirX

                val yardCenterX = 7.5f + 5.0f * dirX - 3.0f * perpX
                val yardCenterY = 7.5f + 5.0f * dirY - 3.0f * perpY

                val labelX = yardCenterX + 1.8f * dirX
                val labelY = yardCenterY + 1.8f * dirY

                val xDp = with(density) { labelX * cellSizePx }
                val yDp = with(density) { labelY * cellSizePx }

                val isActiveTurn = gameState.currentTurnPlayerIndex == p && gameState.winnerPlayerIndex == null
                val finishedCount = gameState.tokens.filter { it.playerIndex == p && it.state == TokenState.FINISHED }.size

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (xDp - 50.dp.toPx()).toDp() },
                            y = with(density) { (yDp - 18.dp.toPx()).toDp() }
                        )
                        .size(width = 100.dp, height = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = player.name,
                            color = Color(player.colorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.shadow(2.dp, CircleShape)
                        )
                        Text(
                            text = if (finishedCount == 4) "FINISHED" else "$finishedCount/4 Home",
                            color = if (isActiveTurn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 6. Overlay Interactive Dice near each Base
        for (p in 0 until 3) {
            val player = gameState.players.getOrNull(p)
            if (player != null) {
                val angleDeg = p * 120f - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val dirX = cos(angleRad).toFloat()
                val dirY = sin(angleRad).toFloat()
                val perpX = -dirY
                val perpY = dirX

                val yardCenterX = 7.5f + 5.0f * dirX - 3.0f * perpX
                val yardCenterY = 7.5f + 5.0f * dirY - 3.0f * perpY

                val diceX = yardCenterX + 2.3f * perpX
                val diceY = yardCenterY + 2.3f * perpY

                val xDp = with(density) { diceX * cellSizePx }
                val yDp = with(density) { diceY * cellSizePx }
                val diceSizePx = 1.25f * cellSizePx
                val diceSizeDp = with(density) { diceSizePx.toDp() }

                val isActiveTurn = gameState.currentTurnPlayerIndex == p && gameState.winnerPlayerIndex == null

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (xDp - diceSizePx / 2f).toDp() },
                            y = with(density) { (yDp - diceSizePx / 2f).toDp() }
                        )
                ) {
                    LudoInteractiveDice(
                        value = displayDiceValue,
                        color = Color(player.colorHex),
                        isRolling = isDiceRolling && gameState.currentTurnPlayerIndex == p,
                        isActiveTurn = isActiveTurn,
                        isClickable = isActiveTurn && gameState.turnState == LudoTurnState.WAITING_FOR_ROLL,
                        onClick = onDiceClick,
                        playerIndex = p,
                        size = diceSizeDp
                    )
                }
            }
        }

        // 7. Overlay Interactive Tokens
        val tokensGrouped = gameState.tokens.distinctBy { it.playerIndex * 4 + it.id }.groupBy { token ->
            val globalId = token.playerIndex * 4 + token.id
            animatedPositions[globalId] ?: calculateGridCoordForToken(token, 3)
        }

        tokensGrouped.forEach { (gridCoord, tokensInCell) ->
            val primaryLegalTokenId = determinePrimaryLegalTokenInCell(tokensInCell, gameState, legalTokenIds)

            // Sort tokens ascending by priority so that inactive tokens are composed first (bottom)
            // and the top active/selectable token is composed last (topmost).
            val sortedTokens = tokensInCell.sortedWith(
                compareBy<LudoToken> { token ->
                    getStackedTokenPriority(token, gameState, legalTokenIds, primaryLegalTokenId)
                }.thenBy { token ->
                    token.playerIndex * 4 + token.id
                }
            )

            sortedTokens.forEachIndexed { index, token ->
                val isCurrentPlayer = gameState.currentTurnPlayerIndex == token.playerIndex
                val isLegal = isCurrentPlayer && legalTokenIds.contains(token.id) && gameState.turnState == LudoTurnState.WAITING_FOR_TOKEN_SELECTION
                val playerColor = Color(gameState.players[token.playerIndex].colorHex)
                val zIndex = getStackedTokenPriority(token, gameState, legalTokenIds, primaryLegalTokenId)

                val subOffset = calculateSubCellOffset(index, sortedTokens.size, cellSizePx)
                val pixelX = (gridCoord.x * cellSizePx) + subOffset.x
                val pixelY = (gridCoord.y * cellSizePx) + subOffset.y

                val scale = when (sortedTokens.size) {
                    1 -> 1.0f
                    2 -> 0.75f
                    3 -> 0.65f
                    else -> 0.60f
                }

                TokenPieceComposable(
                    pixelPos = Offset(pixelX, pixelY),
                    cellSize = cellSizePx,
                    color = playerColor,
                    isLegal = isLegal,
                    scale = scale,
                    zIndex = zIndex,
                    testTag = "ludo_token_${token.playerIndex}_${token.id}",
                    onClick = { if (isLegal) onTokenClicked(token.id) }
                )
            }
        }
    }
}

@Composable
private fun LudoVictoryOverlay(
    gameState: LudoGameState,
    onPlayAgain: () -> Unit,
    onNewMatch: () -> Unit
) {
    val winnerIdx = gameState.winnerPlayerIndex ?: return
    val winner = gameState.players.getOrNull(winnerIdx) ?: return
    val winnerColor = Color(winner.colorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, winnerColor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "${winner.name} WINS!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = winnerColor
                )

                Text(
                    text = "All 4 tokens reached Home Goal successfully!",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gameState.players.sortedBy { it.finishRank ?: 99 }.forEachIndexed { index, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(p.colorHex).copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${index + 1} ${p.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(p.colorHex)
                            )
                            Text(
                                text = if (p.isFinished) "Finished (${p.finishRank})" else "In Play",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNewMatch,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Modes")
                    }

                    Button(
                        onClick = onPlayAgain,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = winnerColor, contentColor = Color.Black)
                    ) {
                        Text("Play Again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
