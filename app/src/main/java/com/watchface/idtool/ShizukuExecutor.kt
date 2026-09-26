package com.watchface.idtool

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

object ShizukuExecutor {

    /** 单条命令最长等待时间（秒），防止异常命令导致进程无限挂起 */
    private const val PROCESS_TIMEOUT_SECONDS = 20L

    /** Shizuku 服务是否正在运行 */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /** 是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean {
        return try {
            if (!isShizukuRunning()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /** 通过反射调用 Shizuku.newProcess()，返回 Process 对象 */
    private fun newProcess(cmd: Array<String>, env: Array<String>?, dir: String?): Process? {
        return try {
            val method: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, env, dir) as? Process
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通过 Shizuku 执行命令，返回输出文本。
     * 确保进程和流正确关闭，不留残留。
     */
    fun execCommand(cmd: String): String {
        val process = newProcess(arrayOf("sh", "-c", cmd), null, null) ?: return ""
        return try {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            // 消费错误流防止进程阻塞
            process.errorStream.use { it.readBytes() }
            // 带超时等待，超时后强制销毁，避免进程挂起拖死调用方
            process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            ""
        } finally {
            process.destroy()
        }
    }

    /** 通过 Shizuku 读取文件全部内容 */
    fun readFile(path: String): ByteArray {
        val process = newProcess(arrayOf("sh", "-c", "cat \"$path\""), null, null) ?: return ByteArray(0)
        return try {
            val bytes = process.inputStream.use { it.readBytes() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            bytes
        } catch (e: Exception) {
            ByteArray(0)
        } finally {
            process.destroy()
        }
    }

    /** 通过 Shizuku 只读取文件头部指定字节数，大幅减少 IO 和内存占用 */
    fun readPartialFile(path: String, byteCount: Int): ByteArray {
        val process = newProcess(
            arrayOf("sh", "-c", "dd if=\"$path\" bs=$byteCount count=1 2>/dev/null"),
            null, null
        ) ?: return ByteArray(0)
        return try {
            val bytes = process.inputStream.use { it.readBytes() }
            process.errorStream.use { it.readBytes() }
            process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            bytes
        } catch (e: Exception) {
            ByteArray(0)
        } finally {
            process.destroy()
        }
    }

    /** 通过 Shizuku 获取文件信息（大小和修改时间） */
    fun getFileInfo(path: String): Pair<Long, Long> {
        val output = execCommand("stat -c '%s %Y' \"$path\"").trim()
        val parts = output.split(" ")
        if (parts.size >= 2) {
            return Pair(
                parts[0].toLongOrNull() ?: 0L,
                parts[1].toLongOrNull() ?: 0L
            )
        }
        return Pair(0L, 0L)
    }
}
