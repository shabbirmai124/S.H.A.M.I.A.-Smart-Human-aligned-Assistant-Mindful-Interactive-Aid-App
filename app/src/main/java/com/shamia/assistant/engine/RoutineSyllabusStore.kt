package com.shamia.assistant.engine

import android.content.Context
import com.shamia.assistant.model.DeviceControlState
import com.shamia.assistant.model.NoteItem
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.model.RoutineCategory
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import com.shamia.assistant.model.UserIdentityProfile
import org.json.JSONArray
import org.json.JSONObject

class RoutineSyllabusStore(context: Context) {
    private val prefs = context.getSharedPreferences("shamia_private_vault_v1", Context.MODE_PRIVATE)

    fun loadIdentity(): UserIdentityProfile = UserIdentityProfile(
        fullName = prefs.getString("full_name", "Md Shabbir Mia") ?: "Md Shabbir Mia",
        addressAs = prefs.getString("address_as", "Sir") ?: "Sir"
    )

    fun saveIdentity(identity: UserIdentityProfile) {
        prefs.edit()
            .putString("full_name", identity.fullName)
            .putString("address_as", identity.addressAs)
            .apply()
    }

    fun loadSelectedModelId(): String = prefs.getString("selected_model_id", "gemma-4-free") ?: "gemma-4-free"
    fun saveSelectedModelId(id: String) = prefs.edit().putString("selected_model_id", id).apply()

    fun loadEnsembleEnabled(): Boolean = prefs.getBoolean("ensemble_enabled", false)
    fun saveEnsembleEnabled(enabled: Boolean) = prefs.edit().putBoolean("ensemble_enabled", enabled).apply()

    fun loadEnsembleIds(): List<String> {
        val raw = prefs.getString("ensemble_ids", null)
        if (raw.isNullOrBlank()) return listOf("gemma-4-free", "qwen3-8b-chinese", "gpt-oss-20b-free")
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(listOf("gemma-4-free", "qwen3-8b-chinese", "gpt-oss-20b-free"))
    }

    fun saveEnsembleIds(ids: List<String>) {
        prefs.edit().putString("ensemble_ids", JSONArray(ids).toString()).apply()
    }

    fun loadUseResearch(): Boolean = prefs.getBoolean("use_research", true)
    fun saveUseResearch(enabled: Boolean) = prefs.edit().putBoolean("use_research", enabled).apply()

    fun loadOllamaEndpoint(): String = prefs.getString("ollama_endpoint", "http://127.0.0.1:11434") ?: "http://127.0.0.1:11434"
    fun saveOllamaEndpoint(url: String) = prefs.edit().putString("ollama_endpoint", url).apply()

    fun loadServerBaseUrl(): String = prefs.getString("server_base_url", "") ?: ""
    fun saveServerBaseUrl(url: String) = prefs.edit().putString("server_base_url", url).apply()

    fun loadControls(): DeviceControlState = DeviceControlState(
        wifi = prefs.getBoolean("ctrl_wifi", true),
        bluetooth = prefs.getBoolean("ctrl_bt", true),
        dnd = prefs.getBoolean("ctrl_dnd", false),
        flashlight = false,
        brightnessPct = prefs.getInt("ctrl_brightness", 100).coerceIn(25, 100)
    )

    fun saveControls(controls: DeviceControlState) {
        prefs.edit()
            .putBoolean("ctrl_wifi", controls.wifi)
            .putBoolean("ctrl_bt", controls.bluetooth)
            .putBoolean("ctrl_dnd", controls.dnd)
            .putInt("ctrl_brightness", controls.brightnessPct)
            .apply()
    }

    fun loadInstalledModelIds(): Set<String> {
        val raw = prefs.getString("installed_model_ids", null) ?: return setOf("offline-core")
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet() + "offline-core"
        }.getOrDefault(setOf("offline-core"))
    }

    fun saveInstalledModelIds(ids: Set<String>) {
        prefs.edit().putString("installed_model_ids", JSONArray(ids.toList()).toString()).apply()
    }

    fun loadInstalledVoiceIds(): Set<String> {
        val raw = prefs.getString("installed_voice_ids", null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveInstalledVoiceIds(ids: Set<String>) {
        prefs.edit().putString("installed_voice_ids", JSONArray(ids.toList()).toString()).apply()
    }

    fun loadCustomModels(): List<OpenSourceModelSpec> {
        val raw = prefs.getString("custom_models_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                OpenSourceModelSpec(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    provider = o.optString("provider", "Custom Open Source HF/Ollama"),
                    params = o.optString("params", "Custom"),
                    sizeLabel = o.optString("sizeLabel", "User Added"),
                    minRamGb = o.optInt("minRamGb", 4),
                    chipsetNote = o.optString("chipsetNote", "Custom"),
                    runtimeLabel = "Custom Local / Ollama",
                    hfRepo = o.optString("hfRepo").takeIf { it.isNotEmpty() },
                    ollamaTag = o.optString("ollamaTag").takeIf { it.isNotEmpty() },
                    summary = o.optString("summary", "Custom user-added open-weight model."),
                    isCustom = true
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomModels(models: List<OpenSourceModelSpec>) {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("provider", m.provider)
                    .put("params", m.params)
                    .put("sizeLabel", m.sizeLabel)
                    .put("minRamGb", m.minRamGb)
                    .put("chipsetNote", m.chipsetNote)
                    .put("hfRepo", m.hfRepo ?: "")
                    .put("ollamaTag", m.ollamaTag ?: "")
                    .put("summary", m.summary)
            )
        }
        prefs.edit().putString("custom_models_json", arr.toString()).apply()
    }

    fun loadRoutine(): List<RoutineItem> {
        val raw = prefs.getString("routine_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(
                RoutineItem("r1", "07:30 AM", "Morning Warmup & 10-Min English Speaking Practice", RoutineCategory.ROUTINE),
                RoutineItem("r2", "09:30 AM", "University Lectures & Core Concept Notes", RoutineCategory.CLASS),
                RoutineItem("r3", "07:30 PM", "Deep Syllabus Revision & Past Questions", RoutineCategory.STUDY)
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val catName = o.optString("category", RoutineCategory.STUDY.name)
                val cat = RoutineCategory.entries.firstOrNull { it.name == catName } ?: RoutineCategory.STUDY
                RoutineItem(o.getString("id"), o.getString("timeLabel"), o.getString("title"), cat, o.optBoolean("completed", false))
            }
        }.getOrDefault(emptyList())
    }

    fun saveRoutine(items: List<RoutineItem>) {
        val arr = JSONArray()
        items.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("timeLabel", r.timeLabel)
                    .put("title", r.title)
                    .put("category", r.category.name)
                    .put("completed", r.completed)
            )
        }
        prefs.edit().putString("routine_json", arr.toString()).apply()
    }

    fun loadSyllabus(): List<SyllabusItem> {
        val raw = prefs.getString("syllabus_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(
                SyllabusItem("s1", "CS-301", "Algorithms & Data Structures", "Dynamic Programming, Graph Traversal & Complexity", "Unit 3 · Midterm Focus"),
                SyllabusItem("s2", "CS-304", "Operating Systems & Architecture", "Process Scheduling, Virtual Memory & 4nm/6nm SoC Basics", "Unit 2 · Lab Next Week")
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SyllabusItem(
                    id = o.getString("id"),
                    courseCode = o.getString("courseCode"),
                    courseTitle = o.getString("courseTitle"),
                    topic = o.getString("topic"),
                    examNote = o.optString("examNote").takeIf { it.isNotBlank() },
                    completed = o.optBoolean("completed", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveSyllabus(items: List<SyllabusItem>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("courseCode", s.courseCode)
                    .put("courseTitle", s.courseTitle)
                    .put("topic", s.topic)
                    .put("examNote", s.examNote ?: "")
                    .put("completed", s.completed)
            )
        }
        prefs.edit().putString("syllabus_json", arr.toString()).apply()
    }

    fun loadTasks(): List<TaskItem> {
        val raw = prefs.getString("tasks_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(
                TaskItem("t1", "Review daily schedule and focus priorities", isHighPriority = true),
                TaskItem("t2", "Practice 5 minutes of spoken English conversation", isHighPriority = false)
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TaskItem(o.getString("id"), o.getString("title"), o.optBoolean("high", false), o.optBoolean("completed", false))
            }
        }.getOrDefault(emptyList())
    }

    fun saveTasks(items: List<TaskItem>) {
        val arr = JSONArray()
        items.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("title", t.title).put("high", t.isHighPriority).put("completed", t.completed))
        }
        prefs.edit().putString("tasks_json", arr.toString()).apply()
    }

    fun loadNotes(): List<NoteItem> {
        val raw = prefs.getString("notes_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(
                NoteItem("n1", "All phone actions remain strictly gated behind your Permission Vault.")
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NoteItem(o.getString("id"), o.getString("content"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveNotes(items: List<NoteItem>) {
        val arr = JSONArray()
        items.forEach { n ->
            arr.put(JSONObject().put("id", n.id).put("content", n.content))
        }
        prefs.edit().putString("notes_json", arr.toString()).apply()
    }

    fun loadPermissions(): Map<PermissionCapability, PermissionPolicy> {
        return PermissionCapability.entries.associateWith { cap ->
            val saved = prefs.getString("perm_${cap.name}", null)
            PermissionPolicy.entries.firstOrNull { it.name == saved }
                ?: if (cap == PermissionCapability.MANAGE_MEMORY) PermissionPolicy.ALWAYS else PermissionPolicy.ASK
        }
    }

    fun savePermission(cap: PermissionCapability, policy: PermissionPolicy) {
        prefs.edit().putString("perm_${cap.name}", policy.name).apply()
    }

    companion object {
        fun formatCountdown(triggerAtMillis: Long, nowMillis: Long): String {
            val diffSec = ((triggerAtMillis - nowMillis) / 1000L).coerceAtLeast(0L)
            if (diffSec == 0L) return "00:00"
            val hrs = diffSec / 3600L
            val mins = (diffSec % 3600L) / 60L
            val secs = diffSec % 60L
            return if (hrs > 0) {
                "%02d:%02d:%02d".format(hrs, mins, secs)
            } else {
                "%02d:%02d".format(mins, secs)
            }
        }
    }
}
