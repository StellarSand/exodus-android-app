package org.eu.exodus_privacy.exodusprivacy

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.eu.exodus_privacy.exodusprivacy.databinding.ActivityMainBinding
import org.eu.exodus_privacy.exodusprivacy.fragments.dialog.ExodusDialogFragment

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = MainActivity::class.java.simpleName
    private val viewModel: MainActivityViewModel by viewModels()
    private var isBottomNavViewVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val snackbar = Snackbar
            .make(
                binding.fragmentCoordinator,
                R.string.not_connected,
                Snackbar.LENGTH_LONG,
            )
            .setAnchorView(binding.bottomNavView) // Snackbar will appear above bottom nav view
            .setAction(R.string.settings) {
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (ex: android.content.ActivityNotFoundException) {
                    try {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    } catch (ex: android.content.ActivityNotFoundException) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }

        val bottomNavigationView = binding.bottomNavView
        bottomNavigationView.isVisible =
            savedInstanceState?.getBoolean("bottomNavViewVisibility") ?: isBottomNavViewVisible
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        val navController = navHostFragment.navController
        NavigationUI.setupWithNavController(bottomNavigationView, navController)

        // Show or hide the connection message depending on the network
        lifecycleScope.launch {
            viewModel.networkConnection.collect { connected ->
                Log.d(TAG, "Observing Network Connection.")
                if (!connected) snackbar.show()
            }
        }

        viewModel.config.observe(this) { config ->
            Log.d(TAG, "Config was: $config.")
            if (!config["privacy_policy"]?.enable!!) {
                Log.d(TAG, "Policy Agreement was: ${config["privacy_policy"]?.enable!!}")
                ExodusDialogFragment().apply {
                    this.isCancelable = false
                    this.show(supportFragmentManager, TAG)
                }
            }
        }

        // Set Up Navigation
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.appDetailFragment, R.id.trackerDetailFragment -> {
                    hideBottomNavigation(bottomNavigationView)
                }

                else -> {
                    showBottomNavigation(bottomNavigationView)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        viewModel.saveNotificationPermissionRequested(true)
        startInitial()
    }

    private fun startInitial() {
        viewModel.config.observe(this) { config ->
            if (config["privacy_policy"]?.enable!! &&
                !ExodusUpdateService.IS_SERVICE_RUNNING
            ) {
                Log.d(
                    TAG,
                    "Populating database for the first time.",
                )
                val intent = Intent(this, ExodusUpdateService::class.java)
                intent.apply {
                    action = ExodusUpdateService.FIRST_TIME_START_SERVICE
                    startService(this)
                }
            }
        }
    }

    // Hide the bottom navigation bar with animation
    private fun hideBottomNavigation(view: View) {
        view.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            clearAnimation()
            animate()
                .translationY(view.height.toFloat())
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    isVisible = false
                    isBottomNavViewVisible = false
                    setLayerType(View.LAYER_TYPE_NONE, null)
                }
        }
    }

    // Show the bottom navigation bar with animation
    private fun showBottomNavigation(view: View) {
        view.apply {
            isVisible = true
            isBottomNavViewVisible = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            clearAnimation()
            animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { setLayerType(View.LAYER_TYPE_NONE, null) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("bottomNavViewVisibility", isBottomNavViewVisible)
    }
}
