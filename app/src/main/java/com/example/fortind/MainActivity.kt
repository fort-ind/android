package com.example.fortind

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var clockText: TextView
    private lateinit var timeLabel: TextView
    private lateinit var dateText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sideMenu: NavigationView
    private lateinit var menuIcon: ImageView

    private lateinit var splashOverlay: FrameLayout
    private lateinit var splashLogo: ImageView
    private lateinit var splashText: TextView

    private lateinit var homeGroup: Group
    private lateinit var settingsContent: ConstraintLayout

    private lateinit var themeSwatchPurple: View
    private lateinit var themeSwatchBlue: View
    private lateinit var themeSwatchGreen: View
    private lateinit var themeSwatchRed: View
    private lateinit var themeSwatchLight: View

    private var showingSettings = false

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Each palette is a Material 3 theme overlay. Applying one is the whole of theming:
     * the overlay supplies the colour roles, and every view resolves them from
     * ?attr/... when it inflates.
     */
    private val themeOverlays = mapOf(
        "purple" to R.style.ThemeOverlay_Fortind_Purple,
        "blue" to R.style.ThemeOverlay_Fortind_Blue,
        "green" to R.style.ThemeOverlay_Fortind_Green,
        "red" to R.style.ThemeOverlay_Fortind_Red,
        "light" to R.style.ThemeOverlay_Fortind_Light
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

    /**
     * The rows NavigationView builds from the menu resource. They live in a RecyclerView
     * it manages internally, so the staggered entrance reaches them through the list
     * rather than through fields. Degrades to no animation if that child is not there.
     */
    private fun menuRowViews(): List<View> {
        val list = sideMenu.getChildAt(0) as? ViewGroup ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private fun resetMenuForAnimation() {
        menuRowViews().forEach { view ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationX = -120f
            view.scaleX = 0.9f
            view.scaleY = 0.9f
        }
    }

    private fun animateMenu() {
        menuRowViews().forEachIndexed { index, view ->
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
        showingSettings = false
        homeGroup.visibility = View.VISIBLE
        settingsContent.visibility = View.GONE
        sideMenu.setCheckedItem(R.id.nav_home)
    }

    private fun showSettings() {
        showingSettings = true
        homeGroup.visibility = View.GONE
        settingsContent.visibility = View.VISIBLE
        sideMenu.setCheckedItem(R.id.nav_settings)
    }

    /**
     * Persists the palette and recreates the activity so the new overlay is in place
     * before anything inflates. Nothing is recoloured by hand.
     */
    private fun selectTheme(key: String) {
        if (!themeOverlays.containsKey(key) || key == loadSavedTheme()) return

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, key)
            .apply()

        recreate()
    }

    private fun loadSavedTheme(): String {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
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
        // Must run before any view is inflated: the overlay is what gives ?attr/... its
        // values, so it has to be on the theme before setContentView resolves them.
        theme.applyStyle(themeOverlays[loadSavedTheme()] ?: R.style.ThemeOverlay_Fortind_Purple, true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockText = findViewById(R.id.localTimeClock)
        timeLabel = findViewById(R.id.localTimeLabel)
        dateText = findViewById(R.id.localDate)
        drawerLayout = findViewById(R.id.drawerLayout)
        sideMenu = findViewById(R.id.sideMenu)
        menuIcon = findViewById(R.id.menuIcon)

        splashOverlay = findViewById(R.id.splashOverlay)
        splashLogo = findViewById(R.id.splashLogo)
        splashText = findViewById(R.id.splashText)

        homeGroup = findViewById(R.id.homeGroup)
        settingsContent = findViewById(R.id.settingsContent)

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

        sideMenu.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showHome()
                R.id.nav_settings -> showSettings()
                // The remaining destinations are not built yet. Leaving them unhandled
                // keeps the checked row on the section actually on screen.
                else -> return@setNavigationItemSelectedListener false
            }
            drawerLayout.closeDrawer(Gravity.START)
            true
        }

        themeSwatchPurple.setOnClickListener { selectTheme("purple") }
        themeSwatchBlue.setOnClickListener { selectTheme("blue") }
        themeSwatchGreen.setOnClickListener { selectTheme("green") }
        themeSwatchRed.setOnClickListener { selectTheme("red") }
        themeSwatchLight.setOnClickListener { selectTheme("light") }

        // Switching palette recreates the activity, so the visible section has to
        // survive that (and a rotation) rather than snapping back to home.
        if (savedInstanceState?.getBoolean(STATE_SHOWING_SETTINGS) == true) {
            showSettings()
        } else {
            showHome()
        }

        handler.post(clockRunnable)

        // Only a cold start gets the splash; a recreate already showed it.
        if (savedInstanceState == null) {
            runSplashAnimation()
        } else {
            splashOverlay.visibility = View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SHOWING_SETTINGS, showingSettings)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
    }

    private companion object {
        const val PREFS = "fortind_prefs"
        const val KEY_THEME = "theme"
        const val DEFAULT_THEME = "purple"
        const val STATE_SHOWING_SETTINGS = "showingSettings"
    }
}