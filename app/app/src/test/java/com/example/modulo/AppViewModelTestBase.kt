package com.example.modulo

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.SavedStateHandle
import com.example.modulo.helpers.AuthenticationHelper
import com.example.modulo.helpers.ParsingHelper
import com.example.modulo.helpers.SyncingHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File

/**
 * Shared setup for tests that exercise [AppViewModel].
 */
abstract class
AppViewModelTestBase {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected lateinit var mockApplication: Application
    protected lateinit var mockSavedStateHandle: SavedStateHandle
    protected lateinit var viewModel: AppViewModel

    protected val parsingHelper: ParsingHelper = mockk(relaxed = true)
    protected val syncingHelper: SyncingHelper = mockk(relaxed = true)

    /** Preferences the fake DataStore emits. Override [buildPrefs] to change onboarding state. */
    protected open fun buildPrefs(): Preferences =
        preferencesOf(HAS_SEEN_TUTORIAL to true, IS_DRIVE_SYNC_ENABLED to false)

    /** Whether `NetworkHelper.isConnected()` (the one-shot check) should report a live connection. */
    protected open val networkConnected: Boolean = false

    /** Hook for subclasses to set up construction mocks before the ViewModel is created. */
    protected open fun onBeforeViewModelCreated() {}

    /** Hook for subclasses to add teardown (e.g. unmock objects they mocked). */
    protected open fun onTearDown() {}

    @Before
    fun baseSetup() {
        mockApplication = mockk(relaxed = true)
        mockSavedStateHandle = mockk(relaxed = true)

        val mockConnectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val tempDir = File(System.getProperty("java.io.tmpdir"))

        // Drive NetworkHelper.isConnected() deterministically via the ConnectivityManager it reads.
        val network = mockk<android.net.Network>(relaxed = true)
        val capabilities = mockk<android.net.NetworkCapabilities>(relaxed = true)
        every { mockConnectivityManager.activeNetwork } returns if (networkConnected) network else null
        every { mockConnectivityManager.getNetworkCapabilities(any()) } returns capabilities
        every { capabilities.hasCapability(any()) } returns networkConnected

        every { mockApplication.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.filesDir } returns tempDir

        mockkStatic("com.example.modulo.AppViewModelKt")
        val mockDataStore = mockk<DataStore<Preferences>>(relaxed = true)
        every { any<Context>().dataStore } returns mockDataStore
        every { mockDataStore.data } returns flowOf(buildPrefs())

        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } answers { self as NetworkRequest.Builder }
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk(relaxed = true)

        onBeforeViewModelCreated()

        viewModel = AppViewModel(mockApplication, mockSavedStateHandle, parsingHelper)
    }

    @After
    fun baseTearDown() {
        // Stop any timer coroutine a test may have left running on viewModelScope.
        viewModel.pauseTimer()
        unmockkStatic("com.example.modulo.AppViewModelKt")
        unmockkObject(AuthenticationHelper)
        unmockkObject(SyncingHelper.Companion)
        unmockkConstructor(ParsingHelper::class)
        unmockkConstructor(NetworkRequest.Builder::class)
        onTearDown()
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun TestScope.signInWithMockedDrive(email: String = "notes@test.com") {
        mockkObject(SyncingHelper.Companion)
        every { SyncingHelper.getSyncService(any(), any()) } returns syncingHelper
        coEvery { syncingHelper.downloadAppData() } returns null
        viewModel.onAuthenticationSuccess(mockApplication, email)
        advanceUntilIdle()
    }

    protected fun sampleTask(
        id: Long = 1_717_059_306_606,
        module: String = "CS1101S",
        title: String = "Tutorial 5",
        due: String = "2026-06-10",
        type: String = "tutorial",
        done: Boolean = false
    ) = Task(id = id, module = module, title = title, due = due, type = type, done = done)

    protected fun sampleHandbook(
        id: String = "hb-university",
        educationLevel: String = "university",
        academicYear: String = "2026/2027",
        semester: Int = 1
    ) = Handbook(
        id = id,
        educationLevel = educationLevel,
        academicYear = academicYear,
        semester = semester,
        termStart = "2026-08-10",
        termEnd = "2026-11-20"
    )
}