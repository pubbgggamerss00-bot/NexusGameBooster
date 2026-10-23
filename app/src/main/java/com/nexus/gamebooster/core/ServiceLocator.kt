package com.nexus.gamebooster.core

import android.content.Context
import com.nexus.gamebooster.data.SettingsRepository
import com.nexus.gamebooster.games.AndroidInstalledAppSource
import com.nexus.gamebooster.games.AndroidGameLauncher
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.games.GameLauncher
import com.nexus.gamebooster.games.InstalledAppSource
import com.nexus.gamebooster.monitoring.AndroidDeviceInfoRepository
import com.nexus.gamebooster.monitoring.DeviceInfoRepository
import com.nexus.gamebooster.monitoring.MonitoringRepository
import com.nexus.gamebooster.monitoring.SystemMonitoringRepository
import com.nexus.gamebooster.optimizer.BoostEngine
import com.nexus.gamebooster.optimizer.DefaultBoostEngine
import com.nexus.gamebooster.optimizer.BoostPrecheckCollector
import com.nexus.gamebooster.optimizer.actions.AndroidBoostExplicitActions
import com.nexus.gamebooster.optimizer.actions.ApplyProfileAction
import com.nexus.gamebooster.optimizer.actions.BoostExplicitActions
import com.nexus.gamebooster.optimizer.actions.CompileGameAction
import com.nexus.gamebooster.optimizer.actions.DeviceRestoreExecutor
import com.nexus.gamebooster.optimizer.actions.ForceStopBackgroundAction
import com.nexus.gamebooster.optimizer.actions.FreeMemoryAction
import com.nexus.gamebooster.optimizer.actions.PrepareGameLaunchAction
import com.nexus.gamebooster.optimizer.actions.ProtectGameAction
import com.nexus.gamebooster.optimizer.actions.SessionMonitoringAction
import com.nexus.gamebooster.optimizer.actions.SystemTweaksAction
import com.nexus.gamebooster.profiles.ProfileRepository
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.shizuku.ShizukuPrivilege
import com.nexus.gamebooster.update.ApkInstaller
import com.nexus.gamebooster.update.GitHubUpdateClient
import com.nexus.gamebooster.ui.screens.home.BoostViewModel
import com.nexus.gamebooster.ui.screens.home.HomeViewModel
import com.nexus.gamebooster.ui.screens.library.LibraryViewModel
import com.nexus.gamebooster.ui.screens.monitor.MonitorViewModel
import com.nexus.gamebooster.ui.screens.profiles.ProfilesViewModel
import com.nexus.gamebooster.ui.screens.settings.SettingsViewModel
import java.io.File

/**
 * Tiny manual DI container. Initialized once from [com.nexus.gamebooster.NexusGameBoosterApp].
 *
 * This stays hand-written on purpose: no Hilt/Koin, no reflection, no magic —
 * everything the app does is visible in this file. The Phase 2/4/5 seams
 * ([InstalledAppSource], [GameLauncher], `SelectedGamesStore`, `ActiveProfileStore`,
 * `BoostHistoryStore`, [SessionMonitorController]) are interfaces/ownable instances so
 * every ViewModel can be exercised with fakes.
 */
object ServiceLocator {

    /**
     * Wall-clock time when this object (and therefore the app session) was initialized.
     * Used by the Monitor screen for the real session-uptime ticker.
     */
    val sessionStartMillis: Long = System.currentTimeMillis()

    private var initialized = false

    /** Application context (kept for the boost's user-explicit actions — never an Activity). */
    lateinit var appContext: Context
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var installedAppSource: InstalledAppSource
        private set
    lateinit var gameLauncher: GameLauncher
        private set
    lateinit var gameRepository: GameRepository
        private set
    lateinit var monitoringRepository: MonitoringRepository
        private set
    lateinit var deviceInfoRepository: DeviceInfoRepository
        private set
    lateinit var profileRepository: ProfileRepository
        private set

    /**
     * Process-scoped session monitoring mode (Phase 5). Written by the boost's
     * session-monitoring action, read by [MonitorViewModel]; in-memory only.
     */
    val sessionMonitorController = SessionMonitorController()

    /**
     * Phase 9 — Shizuku bridge. Started in [init]; status is observable for the UI.
     */
    val shizukuPrivilege = ShizukuPrivilege()

    /** User-explicit boost actions (battery settings / game details / launch) — app context based. */
    val boostExplicitActions: BoostExplicitActions by lazy {
        AndroidBoostExplicitActions(appContext, gameLauncher)
    }

    /**
     * Phase 10 — reverts the device changes recorded in the ledger (Settings →
     * "Restore device changes"). Same honest contract as the boost actions.
     */
    val deviceRestoreExecutor: DeviceRestoreExecutor by lazy {
        DeviceRestoreExecutor(shizukuPrivilege, settingsRepository)
    }

    fun init(appContext: Context) {
        if (initialized) return
        initialized = true
        this.appContext = appContext
        settingsRepository = SettingsRepository.create(appContext)
        installedAppSource = AndroidInstalledAppSource(appContext)
        gameLauncher = AndroidGameLauncher(appContext)
        deviceInfoRepository = AndroidDeviceInfoRepository(appContext)
        gameRepository = GameRepository(
            installedAppSource = installedAppSource,
            gameLauncher = gameLauncher,
            store = settingsRepository, // SettingsRepository implements SelectedGamesStore.
        )
        monitoringRepository = SystemMonitoringRepository(appContext)
        profileRepository = StaticProfileRepository()
        shizukuPrivilege.start()
    }

    /**
     * Builds the boost engine for ONE run.
     *
     * Phase 9 order (aggressive):
     *  1. Apply in-app profile (legacy)
     *  2. Session monitoring mode (legacy, in-app only)
     *  3. Force-stop background launchables (Shizuku)
     *  4. Protect target game from battery/Doze (Shizuku)
     *  5. Trim system caches (Shizuku)
     *  6. ART compile target game (Shizuku)
     *  7. Disable animation scales (Shizuku)
     *  8. Prepare launch (resolve only — actual launch stays user-explicit)
     *
     * Actions that need Shizuku return Unsupported when it is not ready — never fake success.
     * Phase 10: the three device-mutating actions (4/6/7) record their pre-state in the
     * device-change ledger BEFORE writing, so Settings → restore can revert them to the
     * user's real original values.
     */
    fun boostEngine(gamePackageName: String?): BoostEngine = DefaultBoostEngine(
        autoActions = listOf(
            ApplyProfileAction(profileRepository, settingsRepository),
            SessionMonitoringAction(sessionMonitorController),
            ForceStopBackgroundAction(shizukuPrivilege, gamePackageName),
            ProtectGameAction(shizukuPrivilege, gamePackageName, settingsRepository),
            FreeMemoryAction(shizukuPrivilege),
            CompileGameAction(shizukuPrivilege, gamePackageName, changeLedger = settingsRepository),
            SystemTweaksAction(shizukuPrivilege, settingsRepository),
            PrepareGameLaunchAction(gameLauncher, gamePackageName),
        ),
    )

    // ViewModel factories — explicit wiring, one place to see every dependency.

    fun homeViewModel(): HomeViewModel =
        HomeViewModel(
            settingsRepository = settingsRepository,
            monitoringRepository = monitoringRepository,
            deviceInfoRepository = deviceInfoRepository,
            gameRepository = gameRepository,
            profileRepository = profileRepository,
            shizukuPrivilege = shizukuPrivilege,
        )

    fun boostViewModel(): BoostViewModel =
        BoostViewModel(
            engineFactory = ::boostEngine,
            precheckCollector = BoostPrecheckCollector(
                monitoringRepository = monitoringRepository,
                profileRepository = profileRepository,
                activeProfileStore = settingsRepository, // implements ActiveProfileStore
                gameRepository = gameRepository,
            ),
            historyStore = settingsRepository, // implements BoostHistoryStore
            gameRepository = gameRepository,
            explicitActions = boostExplicitActions,
        )

    fun libraryViewModel(): LibraryViewModel =
        LibraryViewModel(gameRepository = gameRepository)

    fun monitorViewModel(): MonitorViewModel =
        MonitorViewModel(
            monitoringRepository = monitoringRepository,
            deviceInfoRepository = deviceInfoRepository,
            profileRepository = profileRepository,
            activeProfileStore = settingsRepository, // SettingsRepository implements ActiveProfileStore.
            sessionMonitorController = sessionMonitorController,
        )

    fun profilesViewModel(): ProfilesViewModel =
        ProfilesViewModel(
            profileRepository = profileRepository,
            activeProfileStore = settingsRepository, // SettingsRepository implements ActiveProfileStore.
        )

    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(
            settingsRepository = settingsRepository,
            gameRepository = gameRepository,
            shizukuPrivilege = shizukuPrivilege,
            restoreExecutor = deviceRestoreExecutor,
            activateShizuku = { shizukuPrivilege.activate(appContext) },
            updateClient = GitHubUpdateClient,
            canRequestInstalls = { ApkInstaller.canRequestInstalls(appContext) },
            requestInstallPermission = {
                // Best-effort: the outcome shows in the card (the switch is the fact).
                ApkInstaller.openInstallPermissionSettings(appContext)
            },
            downloadUpdate = { url, fileName, onProgress ->
                GitHubUpdateClient.downloadApk(
                    url = url,
                    destDir = File(appContext.cacheDir, "updates"),
                    fileName = fileName,
                    onProgress = onProgress,
                )
            },
            startInstall = { file -> ApkInstaller.installApk(appContext, file) },
        )
}
