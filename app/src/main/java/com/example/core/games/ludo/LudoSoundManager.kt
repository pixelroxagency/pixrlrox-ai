package com.example.core.games.ludo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.example.R
import java.util.Collections

class LudoSoundManager(
    private val context: Context,
    var isSoundEnabled: Boolean = true
) : LudoAudioEventListener {

    private val soundPool: SoundPool
    private var diceRollSoundId: Int = 0
    private var tokenReleaseSoundId: Int = 0
    private var tokenStepSoundId: Int = 0
    private var victorySoundId: Int = 0

    private val loadedSoundIds = mutableSetOf<Int>()
    private val activeMediaPlayers = Collections.synchronizedSet(mutableSetOf<MediaPlayer>())

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
                Log.d("LudoSound", "Sound $sampleId loaded successfully")
            } else {
                Log.e("LudoSound", "Failed to load sound $sampleId, status = $status")
            }
        }

        try {
            val pkg = context.packageName
            val diceRes = context.resources.getIdentifier("dice_roll", "raw", pkg)
            val releaseRes = context.resources.getIdentifier("token_release", "raw", pkg)
            val stepRes = context.resources.getIdentifier("token_step", "raw", pkg)
            val vicRes = context.resources.getIdentifier("victory", "raw", pkg)

            if (diceRes != 0) {
                diceRollSoundId = soundPool.load(context, diceRes, 1)
            }
            if (releaseRes != 0) {
                tokenReleaseSoundId = soundPool.load(context, releaseRes, 1)
            }
            if (stepRes != 0) {
                tokenStepSoundId = soundPool.load(context, stepRes, 1)
            }
            if (vicRes != 0) {
                victorySoundId = soundPool.load(context, vicRes, 1)
            }
        } catch (e: Exception) {
            Log.e("LudoSound", "Error loading sound resources", e)
        }
    }

    /**
     * Plays an exact audio clip directly via MediaPlayer bound to applicationContext.
     * The clip plays to completion independently and releases its resources when finished.
     * Playback is not interrupted by turn changes, recompositions, or state updates.
     */
    private fun playDirectMediaResource(rawResId: Int, tag: String, volume: Float = 1.0f) {
        if (!isSoundEnabled) return
        try {
            val mp = MediaPlayer.create(context.applicationContext, rawResId)
            if (mp != null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                mp.setAudioAttributes(attrs)
                mp.setVolume(volume, volume)
                activeMediaPlayers.add(mp)
                mp.setOnCompletionListener { player ->
                    activeMediaPlayers.remove(player)
                    try {
                        player.release()
                    } catch (_: Exception) {}
                }
                mp.setOnErrorListener { player, _, _ ->
                    activeMediaPlayers.remove(player)
                    try {
                        player.release()
                    } catch (_: Exception) {}
                    true
                }
                mp.start()
                Log.d("LudoSound", "Started playback for $tag directly via MediaPlayer (rawRes: $rawResId)")
            } else {
                Log.e("LudoSound", "MediaPlayer.create returned null for $tag (rawRes: $rawResId)")
            }
        } catch (e: Exception) {
            Log.e("LudoSound", "Error playing direct media resource for $tag", e)
        }
    }

    override fun onDiceRoll() {
        if (!isSoundEnabled || diceRollSoundId == 0) return
        try {
            soundPool.play(diceRollSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            Log.d("LudoSound", "Played dice roll sound $diceRollSoundId")
        } catch (e: Exception) {
            Log.e("LudoSound", "Error playing dice roll", e)
        }
    }

    override fun onSix() {
        Log.d("LudoSound", "Playing uploaded Six.mp3 (R.raw.six)")
        playDirectMediaResource(R.raw.six, "Six (six.mp3)")
    }

    override fun onTokenRelease() {
        if (!isSoundEnabled || tokenReleaseSoundId == 0) return
        try {
            soundPool.play(tokenReleaseSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            Log.d("LudoSound", "Played token release sound $tokenReleaseSoundId")
        } catch (e: Exception) {
            Log.e("LudoSound", "Error playing token release", e)
        }
    }

    override fun onTokenStep() {
        if (!isSoundEnabled || tokenStepSoundId == 0) return
        try {
            soundPool.play(tokenStepSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        } catch (e: Exception) {
            Log.e("LudoSound", "Error playing token step", e)
        }
    }

    override fun onCapture() {
        Log.d("LudoSound", "Playing uploaded meow.mp3 (R.raw.meow)")
        playDirectMediaResource(R.raw.meow, "Capture (meow.mp3)")
    }

    override fun onTokenHome() {
        Log.d("LudoSound", "Playing uploaded Khatam.mp3 (R.raw.khatam)")
        playDirectMediaResource(R.raw.khatam, "Finished (khatam.mp3)")
    }

    override fun onVictory() {
        if (!isSoundEnabled || victorySoundId == 0) return
        try {
            soundPool.play(victorySoundId, 1.0f, 1.0f, 3, 0, 1.0f)
            Log.d("LudoSound", "Played victory sound $victorySoundId")
        } catch (e: Exception) {
            Log.e("LudoSound", "Error playing victory", e)
        }
    }

    fun release() {
        try {
            synchronized(activeMediaPlayers) {
                for (mp in activeMediaPlayers) {
                    try {
                        if (mp.isPlaying) {
                            mp.stop()
                        }
                        mp.release()
                    } catch (_: Exception) {}
                }
                activeMediaPlayers.clear()
            }
            soundPool.release()
        } catch (_: Exception) {}
    }
}

