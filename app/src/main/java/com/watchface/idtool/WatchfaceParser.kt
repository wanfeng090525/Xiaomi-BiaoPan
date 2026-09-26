package com.watchface.idtool

import com.watchface.idtool.ui.AppLocale

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object WatchfaceParser {
    private const val MAGIC_OFFSET = 0x00
    private const val MAGIC_VALUE_LE = 0x1234A55A
    private const val ID_OFFSET = 0x28
    private const val ID_FIELD_SIZE = 64
    private const val NAME_OFFSET = 0x68
    private const val NAME_FIELD_SIZE = 64
    private val VALID_DIGIT_LENGTHS = intArrayOf(9, 12)

    /** 只需读取到此偏移即可解析 ID 和名称 */
    const val HEADER_SIZE = NAME_OFFSET + NAME_FIELD_SIZE

    data class WatchfaceInfo(
        val id: String,
        val name: String,
        val size: Long,
        val data: ByteArray
    )

    /** 从完整数据解析 */
    fun parse(data: ByteArray): WatchfaceInfo {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.getInt(MAGIC_OFFSET).toLong() and 0xFFFFFFFFL
        if (magic != (MAGIC_VALUE_LE.toLong() and 0xFFFFFFFFL)) {
            throw Exception(AppLocale.tf("文件格式错误: Magic 不匹配 (0x{0})", magic.toString(16).uppercase()))
        }
        val id = readStringZ(data, ID_OFFSET, ID_FIELD_SIZE)
        val name = readStringZ(data, NAME_OFFSET, NAME_FIELD_SIZE)
        return WatchfaceInfo(id, name, data.size.toLong(), data)
    }

    /** 只从头部数据解析 ID 和名称（不包含完整文件内容） */
    fun parseHeader(header: ByteArray, fullSize: Long): WatchfaceInfo {
        if (header.size < HEADER_SIZE) {
            throw Exception(AppLocale.t("文件过小，无法解析"))
        }
        val buf = ByteBuffer.wrap(header, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.getInt(MAGIC_OFFSET).toLong() and 0xFFFFFFFFL
        if (magic != (MAGIC_VALUE_LE.toLong() and 0xFFFFFFFFL)) {
            throw Exception(AppLocale.tf("文件格式错误: Magic 不匹配 (0x{0})", magic.toString(16).uppercase()))
        }
        val id = readStringZ(header, ID_OFFSET, ID_FIELD_SIZE)
        val name = readStringZ(header, NAME_OFFSET, NAME_FIELD_SIZE)
        return WatchfaceInfo(id, name, fullSize, ByteArray(0))
    }

    /** 高效读取以 null 结尾的字符串，使用 System.arraycopy 替代逐字节添加 */
    private fun readStringZ(data: ByteArray, offset: Int, max: Int): String {
        var end = offset
        val limit = minOf(offset + max, data.size)
        while (end < limit && data[end] != 0.toByte()) end++
        val len = end - offset
        if (len <= 0) return ""
        val bytes = ByteArray(len)
        System.arraycopy(data, offset, bytes, 0, len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun modifyId(
        originalData: ByteArray,
        newId: String,
        newName: String?,
        nameChanged: Boolean
    ): ByteArray {
        val modified = originalData.copyOf()
        val idBytes = newId.toByteArray(StandardCharsets.UTF_8)
        var i = 0
        while (i < idBytes.size && i < ID_FIELD_SIZE) {
            modified[ID_OFFSET + i] = idBytes[i]
            i++
        }
        while (i < ID_FIELD_SIZE) {
            modified[ID_OFFSET + i] = 0
            i++
        }
        if (nameChanged && newName != null) {
            val nameBytes = newName.toByteArray(StandardCharsets.UTF_8)
            var j = 0
            while (j < nameBytes.size && j < NAME_FIELD_SIZE) {
                modified[NAME_OFFSET + j] = nameBytes[j]
                j++
            }
            while (j < NAME_FIELD_SIZE) {
                modified[NAME_OFFSET + j] = 0
                j++
            }
        }
        return modified
    }

    fun validateId(id: String): String? {
        if (id.isEmpty()) return AppLocale.t("ID 不能为空")
        if (!id.all { it.isDigit() }) return AppLocale.t("ID 必须为纯数字")
        if (id.length !in VALID_DIGIT_LENGTHS.toList())
            return AppLocale.tf("ID 位数必须为 9 或 12 位，当前 {0} 位", id.length)
        return null
    }

    /** 加密安全随机源（比 Math.random 更均匀，降低碰撞概率） */
    private val secureRandom = java.security.SecureRandom()

    fun generateRandomId(mode: String): String {
        val digits = when (mode) {
            "random" -> if (secureRandom.nextBoolean()) 9 else 12
            "9" -> 9
            "12" -> 12
            else -> 9
        }
        val sb = StringBuilder(digits)
        sb.append(secureRandom.nextInt(9) + 1) // 首位 1-9，避免前导 0
        for (i in 1 until digits) {
            sb.append(secureRandom.nextInt(10))
        }
        return sb.toString()
    }

    fun saveToDownloads(fileName: String, data: ByteArray): File {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        // 原子写入：先写临时文件再重命名，避免读取到半成品
        val tmp = File(downloadsDir, "$fileName.tmp")
        try {
            FileOutputStream(tmp).use { it.write(data) }
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                // 重命名失败时退回直接写入
                FileOutputStream(file).use { it.write(data) }
            }
        } finally {
            if (tmp.exists() && tmp != file) tmp.delete()
        }
        return file
    }

    fun deleteFromDownloads(fileName: String): Boolean {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloadsDir, fileName)
        return if (file.exists()) file.delete() else false
    }
}
