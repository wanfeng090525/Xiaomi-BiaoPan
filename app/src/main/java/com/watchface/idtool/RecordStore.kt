package com.watchface.idtool

import com.watchface.idtool.ui.AppLocale

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WatchfaceRecord(
    val time: String,
    val oldId: String,
    val newId: String,
    val oldName: String,
    val newName: String,
    val nameChanged: Boolean,
    val fileName: String,
    val fileSize: Long
) {
    /**
     * 优化：懒解析 + 缓存时间戳。
     * 原实现每次调用 displayDate()/isToday() 都会新建 3~4 个 SimpleDateFormat，
     * 在 200 条记录的 LazyColumn 中反复重组时开销显著。
     */
    private val parsedTimeMillis: Long by lazy(LazyThreadSafetyMode.NONE) {
        try {
            inputFormat.get().parse(time)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun displayDate(): String {
        if (parsedTimeMillis == 0L) return time
        return try {
            outputFormat.get().format(Date(parsedTimeMillis))
        } catch (e: Exception) {
            time
        }
    }

    fun isToday(): Boolean {
        if (parsedTimeMillis == 0L) return false
        return try {
            dayFormat.get().format(Date()) == dayFormat.get().format(Date(parsedTimeMillis))
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        // SimpleDateFormat 非线程安全，用 ThreadLocal 缓存避免频繁创建
        private val inputFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        }
        private val outputFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        }
        private val dayFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }
}

object RecordStore {
    private const val PREFS_NAME = "watchface_id_records"
    private const val KEY_RECORDS = "records_v3"

    fun getRecords(context: Context): MutableList<WatchfaceRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<WatchfaceRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(WatchfaceRecord(
                time = o.optString("time", ""),
                oldId = o.optString("oldId", ""),
                newId = o.optString("newId", ""),
                oldName = o.optString("oldName", ""),
                newName = o.optString("newName", ""),
                nameChanged = o.optBoolean("nameChanged", false),
                fileName = o.optString("fileName", ""),
                fileSize = o.optLong("fileSize", 0)
            ))
        }
        return list
    }

    fun addRecord(context: Context, record: WatchfaceRecord) {
        val list = getRecords(context)
        list.add(0, record)
        if (list.size > 200) list.subList(200, list.size).clear()
        saveRecords(context, list)
    }

    fun deleteRecord(context: Context, index: Int) {
        val list = getRecords(context)
        if (index in list.indices) {
            list.removeAt(index)
            saveRecords(context, list)
        }
    }

    fun clearAll(context: Context) {
        saveRecords(context, mutableListOf())
    }

    fun saveRecords(context: Context, list: List<WatchfaceRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("time", r.time)
                put("oldId", r.oldId)
                put("newId", r.newId)
                put("oldName", r.oldName)
                put("newName", r.newName)
                put("nameChanged", r.nameChanged)
                put("fileName", r.fileName)
                put("fileSize", r.fileSize)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    fun exportText(context: Context): String {
        val records = getRecords(context)
        val sb = StringBuilder()
        sb.append(AppLocale.t("表盘 ID 修改记录")).append("\n")
        sb.append(AppLocale.t("导出时间:")).append(" ").append(SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date()))
        sb.append("\n").append(AppLocale.tf("总记录数: {0}", records.size))
        sb.append("\n========================================\n\n")
        records.forEachIndexed { i, r ->
            sb.append("[").append(i + 1).append("] ").append(r.displayDate()).append("\n")
            if (r.nameChanged) {
                sb.append("  ").append(AppLocale.t("原名称:")).append(" ").append(r.oldName.ifEmpty { AppLocale.t("(空)") })
                sb.append("\n  ").append(AppLocale.t("新名称:")).append(" ").append(r.newName.ifEmpty { AppLocale.t("(空)") }).append("\n")
            } else {
                sb.append("  ").append(AppLocale.t("表盘名称:")).append(" ").append(r.newName.ifEmpty { r.oldName.ifEmpty { AppLocale.t("(空)") } }).append("\n")
            }
            sb.append("  ").append(AppLocale.t("原 ID:")).append(" ").append(r.oldId)
            sb.append("\n  ").append(AppLocale.t("新 ID:")).append(" ").append(r.newId)
            sb.append("\n  ").append(AppLocale.t("文件名")).append(": ").append(r.fileName)
            sb.append("\n  ").append(AppLocale.t("文件大小")).append(": ").append(formatBytes(r.fileSize)).append("\n\n")
        }
        return sb.toString()
    }

    fun formatBytes(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0)
        return String.format("%.2f MB", b / 1048576.0)
    }
}
