package com.example.automation

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.model.ActionType
import com.example.data.model.AutomatedActionStep
import com.example.data.model.InstalledAppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceAutomationManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private var isTorchOn: Boolean = false

    /**
     * Executes a single automated action step.
     * Returns true on success or false with error description.
     */
    fun executeStep(step: AutomatedActionStep): Pair<Boolean, String> {
        return try {
            when (step.actionType) {
                ActionType.OPEN_APP -> {
                    val pkg = step.packageName?.ifBlank { null }
                    val appName = step.appName?.ifBlank { null }
                    launchApp(pkg, appName)
                }
                ActionType.OPEN_URL -> {
                    val url = step.url ?: step.query ?: ""
                    openWebUrl(url)
                }
                ActionType.SEARCH_WEB -> {
                    val q = step.query ?: step.description
                    searchWeb(q)
                }
                ActionType.MAKE_CALL -> {
                    val num = step.phoneNumber ?: step.query ?: ""
                    dialNumber(num)
                }
                ActionType.SEND_SMS -> {
                    val num = step.phoneNumber ?: ""
                    val msg = step.messageText ?: step.description
                    sendSms(num, msg)
                }
                ActionType.NAVIGATE -> {
                    val dest = step.location ?: step.query ?: "Current location"
                    navigateToLocation(dest)
                }
                ActionType.SET_ALARM -> {
                    val hour = step.hour ?: 7
                    val min = step.minute ?: 0
                    val msg = step.description.ifBlank { "AgentFlow Alarm" }
                    setAlarm(hour, min, msg)
                }
                ActionType.SET_TIMER -> {
                    val secs = step.seconds ?: 60
                    val label = step.description.ifBlank { "AgentFlow Timer" }
                    setTimer(secs, label)
                }
                ActionType.PLAY_MUSIC -> {
                    val query = step.query ?: step.description
                    playMedia(query)
                }
                ActionType.OPEN_SETTINGS -> {
                    openDeviceSettings(step.settingsType ?: "general")
                }
                ActionType.TOGGLE_FLASHLIGHT -> {
                    toggleFlashlight(step.enableToggle)
                }
                ActionType.COPY_CLIPBOARD -> {
                    val text = step.messageText ?: step.query ?: step.description
                    copyToClipboard(text)
                }
                ActionType.CALENDAR_EVENT -> {
                    val title = step.query ?: step.description
                    val desc = step.messageText ?: "Automated by AgentFlow"
                    createCalendarEvent(title, desc)
                }
                ActionType.SPEAK, ActionType.DEVICE_INFO -> {
                    Pair(true, "Completed")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Failed: ${e.message}")
        }
    }

    /**
     * Launch an app by package name or matching app label.
     */
    fun launchApp(packageName: String?, appNameQuery: String?): Pair<Boolean, String> {
        val targetPackage: String? = if (!packageName.isNullOrBlank()) {
            packageName
        } else if (!appNameQuery.isNullOrBlank()) {
            findPackageByAppName(appNameQuery)
        } else {
            null
        }

        if (targetPackage.isNullOrBlank()) {
            return Pair(false, "App not found for query '${appNameQuery ?: packageName}'")
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            val label = getAppLabel(targetPackage)
            Pair(true, "Successfully launched $label ($targetPackage)")
        } else {
            // Try standard Play Store or browser fallback
            Pair(false, "Cannot launch package: $targetPackage. Activity not found.")
        }
    }

    /**
     * Search installed apps matching a user's verbal or text query.
     */
    fun findPackageByAppName(query: String): String? {
        val cleanQuery = query.lowercase().trim()
        val installed = getInstalledApps()

        // 1. Exact match on app name
        installed.find { it.appName.lowercase() == cleanQuery }?.let { return it.packageName }

        // 2. Contains match
        installed.find { it.appName.lowercase().contains(cleanQuery) }?.let { return it.packageName }

        // 3. Known common app aliases
        val aliases = mapOf(
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "spotify" to "com.spotify.music",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "files" to "com.google.android.documentsui"
        )

        for ((alias, pkg) in aliases) {
            if (cleanQuery.contains(alias)) {
                // verify if installed
                if (installed.any { it.packageName == pkg }) {
                    return pkg
                }
            }
        }

        return null
    }

    /**
     * Scans launcher activities of all installed applications.
     */
    fun getInstalledApps(): List<InstalledAppInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
        val list = mutableListOf<InstalledAppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolve in resolveInfos) {
            val pkg = resolve.activityInfo.packageName
            if (pkg != context.packageName && seenPackages.add(pkg)) {
                val appName = resolve.loadLabel(packageManager).toString()
                val isSystem = (resolve.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                list.add(
                    InstalledAppInfo(
                        appName = appName,
                        packageName = pkg,
                        isSystemApp = isSystem
                    )
                )
            }
        }
        return list.sortedBy { it.appName }
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * Open URL in browser.
     */
    fun openWebUrl(url: String): Pair<Boolean, String> {
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://$formatted"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Pair(true, "Navigated to $formatted")
    }

    /**
     * Perform web search.
     */
    fun searchWeb(query: String): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Searched web for: $query")
        } catch (e: Exception) {
            // Fallback to Google Search URL
            openWebUrl("https://www.google.com/search?q=${Uri.encode(query)}")
        }
    }

    /**
     * Dial a phone number.
     */
    fun dialNumber(number: String): Pair<Boolean, String> {
        val cleanNumber = number.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Pair(true, "Prepared dialer for: $cleanNumber")
    }

    /**
     * Send SMS intent with prefilled recipient & message.
     */
    fun sendSms(phoneNumber: String, message: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        val uri = if (cleanNumber.isNotEmpty()) Uri.parse("smsto:$cleanNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Pair(true, "Opened messaging with pre-filled text")
    }

    /**
     * Launch turn-by-turn navigation or place search on Google Maps.
     */
    fun navigateToLocation(location: String): Pair<Boolean, String> {
        val uri = Uri.parse("google.navigation:q=${Uri.encode(location)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(mapIntent)
            Pair(true, "Started navigation to: $location")
        } catch (e: Exception) {
            // Fallback to geo URI or web maps
            val fallbackUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(location)}")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            Pair(true, "Opened maps for: $location")
        }
    }

    /**
     * Set device alarm clock.
     */
    fun setAlarm(hour: Int, minute: Int, message: String): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
            Pair(true, "Alarm set for $timeStr ($message)")
        } catch (e: Exception) {
            Pair(false, "Could not set alarm: ${e.message}")
        }
    }

    /**
     * Set device countdown timer.
     */
    fun setTimer(seconds: Int, label: String): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Timer started for $seconds seconds ($label)")
        } catch (e: Exception) {
            Pair(false, "Could not set timer: ${e.message}")
        }
    }

    /**
     * Play music or video.
     */
    fun playMedia(query: String): Pair<Boolean, String> {
        // Try MediaStore search first
        val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(mediaIntent)
            Pair(true, "Playing media: $query")
        } catch (e: Exception) {
            // YouTube / Browser fallback
            val ytUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            val ytIntent = Intent(Intent.ACTION_VIEW, ytUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(ytIntent)
            Pair(true, "Searching and playing on YouTube: $query")
        }
    }

    /**
     * Open device settings panel.
     */
    fun openDeviceSettings(type: String): Pair<Boolean, String> {
        val action = when (type.lowercase().trim()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "audio", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display", "screen" -> Settings.ACTION_DISPLAY_SETTINGS
            "airplane", "airplanemode" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Pair(true, "Opened $type settings")
    }

    /**
     * Toggle hardware camera torch / flashlight.
     */
    fun toggleFlashlight(turnOn: Boolean): Pair<Boolean, String> {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                return Pair(false, "Flashlight hardware not available on this device")
            }

            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            val stateStr = if (turnOn) "ON" else "OFF"
            Pair(true, "Flashlight turned $stateStr")
        } catch (e: CameraAccessException) {
            Pair(false, "Camera access error: ${e.message}")
        } catch (e: Exception) {
            Pair(false, "Flashlight toggle error: ${e.message}")
        }
    }

    /**
     * Copy text to Android clipboard.
     */
    fun copyToClipboard(text: String): Pair<Boolean, String> {
        val clip = ClipData.newPlainText("AgentFlow Copy", text)
        clipboardManager.setPrimaryClip(clip)
        return Pair(true, "Copied to clipboard: \"$text\"")
    }

    /**
     * Create Calendar Event.
     */
    fun createCalendarEvent(title: String, description: String): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 60 * 60 * 1000)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Added calendar event draft: $title")
        } catch (e: Exception) {
            Pair(false, "Calendar not supported: ${e.message}")
        }
    }

    /**
     * Gets a readable snapshot of current device state to inform the AI model.
     */
    fun getDeviceStatusSnapshot(): String {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val connType = if (isWifi) "Wi-Fi" else if (isCellular) "Cellular" else "Offline"

        val timeStr = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.getDefault()).format(Date())

        return "Current Time: $timeStr | Battery: $batteryPct% (Charging: $isCharging) | Network: $connType"
    }
}
