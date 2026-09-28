package com.example.core.games.ludo

interface LudoAudioEventListener {
    fun onDiceRoll()
    fun onSix()
    fun onTokenRelease()
    fun onTokenStep()
    fun onCapture()
    fun onTokenHome()
    fun onVictory()
}
