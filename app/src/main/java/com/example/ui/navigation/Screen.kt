package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Ana Sayfa", Icons.Default.Home),
    CHAT("chat", "Soru-Cevap", Icons.Default.Chat),
    NOTES("notes", "Ders Notları", Icons.Default.NoteAlt),
    QUIZ("quiz", "Sınav & Test", Icons.Default.Assignment),
    SETTINGS("settings", "Ayarlar", Icons.Default.Settings);

    companion object {
        val bottomNavScreens = listOf(HOME, CHAT, NOTES, QUIZ, SETTINGS)
    }
}
