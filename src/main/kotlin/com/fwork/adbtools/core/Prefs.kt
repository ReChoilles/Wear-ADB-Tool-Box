package com.fwork.adbtools.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyfishxu.kadb.DelayedAckMode
import com.flyfishxu.kadb.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfishxu.kadb.mdns.MdnsServiceType
import okio.Path.Companion.toPath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/** 应用偏好与 Kadb 主机身份持久化。 */
object Prefs {
    var firstLoad by mutableStateOf(true)
        private set
    var darkMode by mutableStateOf(false)
    var swatchIndex by mutableIntStateOf(0)
    var customHex by mutableStateOf<String?>(null)
    var connectTimeoutSec by mutableIntStateOf(0)
    var socketTimeoutSec by mutableIntStateOf(0)
    var delayedAckMode by mutableStateOf(DelayedAckMode.AOSP_DEFAULT)
    var mdnsEnabled by mutableStateOf(true)
    var mdnsTypes by mutableStateOf(setOf(MdnsServiceType.TLS_CONNECT, MdnsServiceType.TLS_PAIRING, MdnsServiceType.ADB))

    private val props = Properties()
    private lateinit var appDir: File
    private lateinit var prefsFile: File

    val keyStoreFile: File by lazy {
        File(appDir, "kadb").let { if (!it.exists()) it.mkdirs(); File(it, "adbkey") }
    }

    fun load() {
        appDir = File(
            System.getenv("APPDATA") ?: (System.getProperty("user.home") + File.separator + ".wearadb"),
            "WearADBToolBox",
        ).also { if (!it.exists()) it.mkdirs() }
        prefsFile = File(appDir, "prefs.properties")
        runCatching {
            if (prefsFile.exists()) FileInputStream(prefsFile).use { props.load(it) }
        }.onSuccess {
            if (props.isEmpty) firstLoad = true else firstLoad = false
        }.onFailure { firstLoad = true }

        darkMode = props.getProperty("dark", false.toString()).toBoolean()
        swatchIndex = props.getProperty("swatch", "0").toIntOrNull()?.coerceIn(-1, 3) ?: 0
        customHex = props.getProperty("customHex")?.takeIf { it.isNotBlank() }
        connectTimeoutSec = props.getProperty("connectTimeoutSec", "0").toIntOrNull() ?: 0
        socketTimeoutSec = props.getProperty("socketTimeoutSec", "0").toIntOrNull() ?: 0
        delayedAckMode = props.getProperty("delayedAck", DelayedAckMode.AOSP_DEFAULT.name)
            .let { name -> DelayedAckMode.entries.firstOrNull { it.name == name } } ?: DelayedAckMode.AOSP_DEFAULT
    }

    /** 首次运行跟随系统深色主题（一次写入后不再跟随）。 */
    fun applyFirstRunTheme(systemDark: Boolean) {
        if (firstLoad) {
            darkMode = systemDark
            firstLoad = false
            persist()
        }
    }

    @Synchronized
    fun persist() {
        props.setProperty("dark", darkMode.toString())
        props.setProperty("swatch", swatchIndex.toString())
        customHex?.let { props.setProperty("customHex", it) } ?: props.remove("customHex")
        props.setProperty("connectTimeoutSec", connectTimeoutSec.toString())
        props.setProperty("socketTimeoutSec", socketTimeoutSec.toString())
        props.setProperty("delayedAck", delayedAckMode.name)
        runCatching {
            FileOutputStream(prefsFile).use { props.store(it, "Wear ADB Tool Box") }
        }
    }
}

/** 数据目录（用于密钥持久化等）。 */
object AppDirs {
    val base: File = File(
        System.getenv("APPDATA") ?: (System.getProperty("user.home") + File.separator + ".wearadb"),
        "WearADBToolBox",
    ).also { if (!it.exists()) it.mkdirs() }

    val historyFile: File get() = File(base, "history.properties")
}

/** 启动时配置 Kadb 主机身份（持久化 RSA 密钥，避免每次启动重新授权）。 */
fun configureKadbIdentity() {
    val keyPath = Prefs.keyStoreFile.parentFile?.absolutePath + File.separator + Prefs.keyStoreFile.name
    runCatching {
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(keyPath.toPath()),
            policy = KadbCertPolicy(),
        )
    }.onFailure {
        LogBus.warn("主机密钥配置失败，将使用内存身份：${it.message}")
    }
}
