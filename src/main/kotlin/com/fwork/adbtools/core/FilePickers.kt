package com.fwork.adbtools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/** Swing 文件选择器（EDT 打开模态框，协程等待结果）。 */
object FilePickers {

    /** 选择多个 APK。 */
    suspend fun pickApks(): List<File> {
        val future = CompletableFuture<List<File>>()
        SwingUtilities.invokeLater {
            val chooser = JFileChooser()
            chooser.dialogTitle = "选择 APK 文件"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            chooser.isMultiSelectionEnabled = true
            chooser.fileFilter = FileNameExtensionFilter("APK 文件 (*.apk)", "apk")
            val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
            future.complete(if (approved) chooser.selectedFiles.toList() else emptyList())
        }
        return awaitOnIo(future) ?: emptyList()
    }

    /** 选择目录。 */
    suspend fun pickDirectory(title: String): File? {
        val future = CompletableFuture<File?>()
        SwingUtilities.invokeLater {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.isMultiSelectionEnabled = false
            val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
            future.complete(if (approved) chooser.selectedFile else null)
        }
        return awaitOnIo(future)
    }

    /** 选择任意文件（不限扩展名）。 */
    suspend fun pickAnyFile(title: String): File? {
        val future = CompletableFuture<File?>()
        SwingUtilities.invokeLater {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            chooser.isMultiSelectionEnabled = false
            chooser.acceptAllFileFilter
            val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
            future.complete(if (approved) chooser.selectedFile else null)
        }
        return awaitOnIo(future)
    }

    /** 保存对话框（无预选文件时使用 presetName）。确认后自动补 “.pem” 扩展名。 */
    suspend fun pickSaveFile(title: String, presetName: String): File? {
        val future = CompletableFuture<File?>()
        SwingUtilities.invokeLater {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            chooser.selectedFile = File(presetName)
            val approved = chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION
            future.complete(if (approved) ensurePem(chooser.selectedFile) else null)
        }
        return awaitOnIo(future)
    }

    private fun ensurePem(file: File?): File? {
        if (file == null) return null
        return if (file.extension.isBlank()) File(file.parentFile, file.name + ".pem") else file
    }

    private suspend fun <T> awaitOnIo(future: CompletableFuture<T>): T =
        withContext(Dispatchers.IO) { future.get() }
}
