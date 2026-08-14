package com.example.fortind

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var menuLink: TextView
    private lateinit var menuHelp: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val activeColor = Color.parseColor("#8740B3")
    private val inactiveColor = Color.TRANSPARENT

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
        setMenuColor(menuLink, inactiveColor)
        setMenuColor(menuHelp, inactiveColor)

        setMenuColor(selected, activeColor)
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
        menuLink = findViewById(R.id.menuLink)
        menuHelp = findViewById(R.id.menuHelp)

        timeLabel.text = "Current Time in ${getSimpleCityName()}"

        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(Gravity.START)
        }

        selectMenuItem(menuHome)

        handler.post(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
    }
}