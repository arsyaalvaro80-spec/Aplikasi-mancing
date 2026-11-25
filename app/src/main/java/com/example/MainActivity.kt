package com.example

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.ai.FirebaseSonarAiRepository
import com.example.data.ai.configureAppCheck
import com.example.data.local.SonarDatabase
import com.example.data.local.WaypointRepository
import com.example.data.map.MarineTileManager
import com.example.data.marine.TideMarineRepository
import com.example.data.preferences.SonarPreferences
import com.example.data.weather.WeatherRadarRepository
import com.example.sensors.CompassSensorManager
import com.example.sensors.LocationTracker
import com.example.ui.BintanSonarApp
import com.example.ui.theme.BintanSonarTheme
import com.example.viewmodel.SonarViewModel

class MainActivity : ComponentActivity() {

    private val sonarViewModel: SonarViewModel by viewModels {
        val appContext = applicationContext
        val database = SonarDatabase.getInstance(appContext)
        SonarViewModel.Factory(
            appContext = appContext,
            waypointRepository = WaypointRepository(database.waypointDao()),
            preferences = SonarPreferences(appContext),
            aiRepository = FirebaseSonarAiRepository(),
            locationTracker = LocationTracker(appContext),
            compassSensorManager = CompassSensorManager(appContext),
            tileManager = MarineTileManager(appContext),
            tideRepository = TideMarineRepository(),
            weatherRepository = WeatherRadarRepository()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            BintanSonarTheme {
                BintanSonarApp(
                    viewModel = sonarViewModel,
                    onKeepScreenAwakeChanged = { keepOn ->
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                )
            }
        }
    }
}
