package com.example.fortind

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.drawerlayout.widget.DrawerLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var clockText: TextView
    private lateinit var timeLabel: TextView
    private lateinit var dateText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuIcon: ImageView

    private lateinit var menuHome: TextView
    private lateinit var menuWhatsNew: TextView
    private lateinit var menuFortServer: TextView
    private lateinit var menuGames: TextView
    private lateinit var menuSocial: TextView
    private lateinit var menuEmulators: TextView
    private lateinit var menuApps: TextView
    private lateinit var menuLabs: TextView
    private lateinit var menuExtras: TextView
    private lateinit var menuSettings: TextView
    private lateinit var menuLink: TextView
    private lateinit var menuHelp: TextView

    private lateinit var splashOverlay: FrameLayout
    private lateinit var splashLogo: ImageView
    private lateinit var splashText: TextView

    private lateinit var homeGroup: Group
    private lateinit var settingsContent: ConstraintLayout

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var backgroundImage: ImageView
    private lateinit var sideMenu: ScrollView
    private lateinit var themeSwatchPurple: View
    private lateinit var themeSwatchBlue: View
    private lateinit var themeSwatchGreen: View
    private lateinit var themeSwatchRed: View
    private lateinit var themeSwatchLight: View

    private val handler = Handler(Looper.getMainLooper())

    private var activeColor = Color.parseColor("#8740B3")
    private val inactiveColor = Color.TRANSPARENT

    private data class ThemeColors(val background: Int, val drawer: Int, val accent: Int, val patternRes: Int?, val isLight: Boolean = false)

    private val themes = mapOf(
        "purple" to ThemeColors(Color.parseColor("#3D0C5D"), Color.parseColor("#4D0086"), Color.parseColor("#8B42B8"), R.drawable.bg_pattern_purple),
        "blue" to ThemeColors(Color.parseColor("#0C205D"), Color.parseColor("#132C86"), Color.parseColor("#2F62E0"), R.drawable.bg_pattern_blue),
        "green" to ThemeColors(Color.parseColor("#0C5D2B"), Color.parseColor("#0F7E3C"), Color.parseColor("#30AD5B"), R.drawable.bg_pattern_green),
        "red" to ThemeColors(Color.parseColor("#5D0C0C"), Color.parseColor("#861313"), Color.parseColor("#E0452F"), R.drawable.bg_pattern_red),
        "light" to ThemeColors(Color.parseColor("#F5F5F5"), Color.parseColor("#FFFFFF"), Color.parseColor("#E0E0E0"), null, isLight = true)
    )

    private val clockRunnable = object : Runnable {
        override fun run() {
            val timeFormat = SimpleDateFormat("h:mm:ss", Locale.getDefault())
            val amPmFormat = SimpleDateFormat("a", Locale.getDefault())

            val now = Date()
            val timePart = timeFormat.format(now)
            val amPmPart = amPmFormat.format(now).lowercase()

            val fullText = "$timePart $amPmPart"
            val spannable = SpannableString(fullText)

            val amPmStart = timePart.length + 1

            spannable.setSpan(
                RelativeSizeSpan(0.5f),
                amPmStart,
                fullText.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            clockText.text = spannable

            val dateFormat = SimpleDateFormat("EEE, MMMM d", Locale.getDefault())
            dateText.text = dateFormat.format(now)

            handler.postDelayed(this, 1000)
        }
    }

    private fun getSimpleCityName(): String {
        val zoneId = TimeZone.getDefault().id
        val cityPart = zoneId.substringAfterLast("/")
        return cityPart.replace("_", " ")
    }

    private fun setMenuColor(view: TextView, color: Int) {
        view.setBackgroundColor(color)
    }

    private fun selectMenuItem(selected: TextView) {
        setMenuColor(menuHome, inactiveColor)
        setMenuColor(menuWhatsNew, inactiveColor)
        setMenuColor(menuFortServer, inactiveColor)
        setMenuColor(menuGames, inactiveColor)
        setMenuColor(menuSocial, inactiveColor)
        setMenuColor(menuEmulators, inactiveColor)
        setMenuColor(menuApps, inactiveColor)
        setMenuColor(menuLabs, inactiveColor)
        setMenuColor(menuExtras, inactiveColor)
        setMenuColor(menuSettings, inactiveColor)
        setMenuColor(menuLink, inactiveColor)
        setMenuColor(menuHelp, inactiveColor)

        setMenuColor(selected, activeColor)
    }

    private fun menuItems() = listOf(
        menuHome,
        menuWhatsNew,
        menuFortServer,
        menuGames,
        menuSocial,
        menuEmulators,
        menuApps,
        menuLabs,
        menuExtras,
        menuSettings,
        menuLink,
        menuHelp
    )

    private fun resetMenuForAnimation() {
        menuItems().forEach { view ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationX = -120f
            view.scaleX = 0.9f
            view.scaleY = 0.9f
        }
    }

    private fun animateMenu() {
        menuItems().forEachIndexed { index, view ->
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 40L)
                .setDuration(300L)
                .setInterpolator(OvershootInterpolator(0.75f))
                .start()
        }
    }

    private fun showHome() {
        homeGroup.visibility = View.VISIBLE
        settingsContent.visibility = View.GONE
        selectMenuItem(menuHome)
    }

    private fun showSettings() {
        homeGroup.visibility = View.GONE
        settingsContent.visibility = View.VISIBLE
        selectMenuItem(menuSettings)
    }

    private fun applyTextColor(view: View, color: Int) {
        if (view.id == R.id.splashOverlay || view.id == R.id.topBar) return

        if (view is TextView) {
            view.setTextColor(color)
        }

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextColor(view.getChildAt(i), color)
            }
        }
    }

    private fun applyTheme(key: String) {
        val theme = themes[key] ?: return

        mainLayout.setBackgroundColor(theme.background)

        if (theme.patternRes != null) {
            backgroundImage.visibility = View.VISIBLE
            backgroundImage.clearColorFilter()
            backgroundImage.setImageResource(theme.patternRes)
            backgroundImage.alpha = 1.0f
        } else {
            backgroundImage.visibility = View.GONE
        }

        sideMenu.setBackgroundColor(theme.drawer)
        activeColor = theme.accent

        val textColor = if (theme.isLight) Color.parseColor("#1A1A1A") else Color.WHITE
        applyTextColor(mainLayout, textColor)
        applyTextColor(sideMenu, textColor)

        menuItems().forEach { it.setTextColor(textColor) }

        selectMenuItem(if (settingsContent.visibility == View.VISIBLE) menuSettings else menuHome)

        getSharedPreferences("fortind_prefs", MODE_PRIVATE)
            .edit()
            .putString("theme", key)
            .apply()
    }

    private fun loadSavedTheme(): String {
        return getSharedPreferences("fortind_prefs", MODE_PRIVATE)
            .getString("theme", "purple") ?: "purple"
    }

    private fun runSplashAnimation() {
        splashLogo.post {
            val slideDistance = splashLogo.width.toFloat() / 2f + 24f

            splashText.translationX = 80f

            splashOverlay.postDelayed({
                splashLogo.animate()
                    .translationX(-slideDistance)
                    .setDuration(450L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                splashText.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(450L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                splashOverlay.postDelayed({
                    splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(350L)
                        .withEndAction {
                            splashOverlay.visibility = View.GONE
                        }
                        .start()
                }, 650L)
            }, 700L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockText = findViewById(R.id.localTimeClock)
        timeLabel = findViewById(R.id.localTimeLabel)
        dateText = findViewById(R.id.localDate)
        drawerLayout = findViewById(R.id.drawerLayout)
        menuIcon = findViewById(R.id.menuIcon)

        menuHome = findViewById(R.id.menuHome)
        menuWhatsNew = findViewById(R.id.menuWhatsNew)
        menuFortServer = findViewById(R.id.menuFortServer)
        menuGames = findViewById(R.id.menuGames)
        menuSocial = findViewById(R.id.menuSocial)
        menuEmulators = findViewById(R.id.menuEmulators)
        menuApps = findViewById(R.id.menuApps)
        menuLabs = findViewById(R.id.menuLabs)
        menuExtras = findViewById(R.id.menuExtras)
        menuSettings = findViewById(R.id.menuSettings)
        menuLink = findViewById(R.id.menuLink)
        menuHelp = findViewById(R.id.menuHelp)

        splashOverlay = findViewById(R.id.splashOverlay)
        splashLogo = findViewById(R.id.splashLogo)
        splashText = findViewById(R.id.splashText)

        homeGroup = findViewById(R.id.homeGroup)
        settingsContent = findViewById(R.id.settingsContent)

        mainLayout = findViewById(R.id.main)
        backgroundImage = findViewById(R.id.backgroundImage)
        sideMenu = findViewById(R.id.sideMenu)
        themeSwatchPurple = findViewById(R.id.themeSwatchPurple)
        themeSwatchBlue = findViewById(R.id.themeSwatchBlue)
        themeSwatchGreen = findViewById(R.id.themeSwatchGreen)
        themeSwatchRed = findViewById(R.id.themeSwatchRed)
        themeSwatchLight = findViewById(R.id.themeSwatchLight)

        timeLabel.text = "Current Time in ${getSimpleCityName()}"

        menuIcon.setOnClickListener {
            resetMenuForAnimation()
            drawerLayout.openDrawer(Gravity.START)

            drawerLayout.postDelayed({
                animateMenu()
            }, 100)
        }

        menuHome.setOnClickListener {
            showHome()
            drawerLayout.closeDrawer(Gravity.START)
        }

        menuSettings.setOnClickListener {
            showSettings()
            drawerLayout.closeDrawer(Gravity.START)
        }

        themeSwatchPurple.setOnClickListener { applyTheme("purple") }
        themeSwatchBlue.setOnClickListener { applyTheme("blue") }
        themeSwatchGreen.setOnClickListener { applyTheme("green") }
        themeSwatchRed.setOnClickListener { applyTheme("red") }
        themeSwatchLight.setOnClickListener { applyTheme("light") }

        applyTheme(loadSavedTheme())

        selectMenuItem(menuHome)

        handler.post(clockRunnable)

        runSplashAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
    }
}