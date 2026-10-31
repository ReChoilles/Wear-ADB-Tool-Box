package com.fwork.adbtools

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fwork.adbtools.core.Prefs
import com.fwork.adbtools.core.configureKadbIdentity
import com.fwork.adbtools.theme.Md3eTheme
import com.fwork.adbtools.ui.AppRoot

fun main() {
    // 一次性初始化（Window 内容为可组合函数，副作用放在组合外）
    Prefs.load()
    configureKadbIdentity()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Wear ADB Tool Box · Kadb 桌面版",
            state = rememberWindowState(width = 1360.dp, height = 880.dp),
        ) {
            Md3eTheme {
                AppRoot()
            }
        }
    }
}
