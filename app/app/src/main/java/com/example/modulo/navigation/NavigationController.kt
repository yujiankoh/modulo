package com.example.modulo.navigation

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.credentials.CredentialManager
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.modulo.AppViewModel
import com.example.modulo.EducationLevel
import com.example.modulo.StartupState
import com.example.modulo.helpers.AuthenticationHelper
import com.example.modulo.pages.AddTaskPage
import com.example.modulo.pages.AllNotesPage
import com.example.modulo.pages.AllTaskPage
import com.example.modulo.pages.AuthenticatePage
import com.example.modulo.pages.CalendarPage
import com.example.modulo.pages.GPAPage
import com.example.modulo.pages.HandbookCreatePage
import com.example.modulo.pages.HandbookPage
import com.example.modulo.pages.HomePage
import com.example.modulo.pages.LoadingPage
import com.example.modulo.pages.TimetableManualPage
import com.example.modulo.pages.SettingsPage
import com.example.modulo.pages.SignInPage
import com.example.modulo.pages.StudyHistoryPage
import com.example.modulo.pages.StudySessionPage
import com.example.modulo.pages.TimetablePage
import com.example.modulo.pages.TimetableStateOverlay
import com.example.modulo.pages.TutorialPage
import com.example.modulo.pages.UploadTimetablePage
import kotlinx.serialization.Serializable

@Serializable object StartupGraph
@Serializable object AppGraph

@Serializable object Loading
@Serializable object Tutorial
@Serializable object SignIn
@Serializable object Authenticate

@Serializable object Home
@Serializable data class AddTask(val moduleCode: String? = null)
@Serializable object Calendar
@Serializable object AllTasks
@Serializable object StudySession

@Serializable object TimetableUpload
@Serializable data class TimetableManual(val educationLevel: EducationLevel, val isEditMode: Boolean = false)
@Serializable object Settings
@Serializable object Timetable
@Serializable object GPA
@Serializable object Notes
@Serializable object StudyHistory
@Serializable object HandbookCreate
@Serializable object Handbook
@Serializable object HandbookEdit

@Composable
fun RootNavigation(
    viewModel: AppViewModel,
) {
    val navController = rememberNavController()
    val startupState by viewModel.startupState.collectAsState()
    val timetableState by viewModel.timetableState.collectAsState()

    val context = requireNotNull(LocalActivity.current)
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val navigateToHomeAfterSync: (String) -> Unit = { email ->
        viewModel.onAuthenticationSuccess(context, email)
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        AuthenticationHelper.onDrivePermission(
            activity = context,
            result = result,
            email = viewModel.getUserEmail(),
            onSuccess = navigateToHomeAfterSync
        )
    }

    val triggerSignIn = {
        AuthenticationHelper.authenticateThenAuthorize(
            activity = context,
            scope = scope,
            credentialManager = credentialManager,
            onLaunchIntent = { intentRequest, userEmail ->
                viewModel.setUserEmail(userEmail)
                driveAuthorizationLauncher.launch(intentRequest)
            },
            onSuccess = navigateToHomeAfterSync,
            onProfile = { url -> viewModel.setUserPhotoUrl(url) }
        )
    }

    LaunchedEffect(startupState) {
        when (startupState) {
            StartupState.TUTORIAL -> {
                navController.navigate(Tutorial) {
                    popUpTo(Loading) { inclusive = true }
                }
            }
            StartupState.SIGN_IN -> {
                navController.navigate(SignIn) {
                    popUpTo(Loading) { inclusive = true }
                }
            }
            StartupState.AUTHENTICATE -> {
                navController.navigate(Authenticate) {
                    popUpTo(Loading) { inclusive = true }
                }
            }
            StartupState.HANDBOOK -> {
                navController.navigate(HandbookCreate) {
                    popUpTo(Loading) { inclusive = true }
                }
            }
            StartupState.READY -> {
                navController.navigate(AppGraph) {
                    popUpTo(StartupGraph) { inclusive = true }
                    popUpTo(HandbookCreate) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = StartupGraph
        ) {
            startupNavigation(
                navController = navController,
                viewModel = viewModel,
                onAuthentication = triggerSignIn
            )

            globalNavigation(
                navController = navController,
                viewModel = viewModel,
            )

            appNavigation(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(Settings) },
                onNavigateToTimetableUpload = { navController.navigate(TimetableUpload) },
                onNavigateToStudyHistory = { navController.navigate(StudyHistory) },
                onNavigateToTimetable = { navController.navigate(Timetable) }
            )
        }

        TimetableStateOverlay(
            state = timetableState,
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .zIndex(100f)
        )
    }
}

fun NavGraphBuilder.startupNavigation(
    navController: NavHostController,
    viewModel: AppViewModel,
    onAuthentication: () -> Unit
) {
    navigation<StartupGraph>(startDestination = Loading) {
        composable<Loading> { LoadingPage() }

        composable<Tutorial> {
            TutorialPage(
                onSkipClick = {
                    viewModel.completeTutorial()
                }
            )
        }

        composable<SignIn> {
            SignInPage(
                onSyncWithDriveClick = { navController.navigate(Authenticate) },
                onLocalSaveClick = {
                    viewModel.saveSyncPreference(false)
                }
            )
        }

        composable<Authenticate> {
            AuthenticatePage(
                onSyncWithDriveClick = {
                    viewModel.saveSyncPreference(true)
                    onAuthentication()
                }
            )
        }
    }
}

fun NavGraphBuilder.globalNavigation(
    navController: NavHostController,
    viewModel: AppViewModel,
) {
    composable<TimetableUpload> {
        UploadTimetablePage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onManualClick = { educationLevel ->
                navController.navigate(TimetableManual(educationLevel))
            }
        )
    }

    composable<TimetableManual> { backStackEntry ->
        val args = backStackEntry.toRoute<TimetableManual>()

        TimetableManualPage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            currEducationLevel = args.educationLevel,
            isEditMode = args.isEditMode
        )
    }

    composable<Settings> {
        SettingsPage (
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onNavigateToTimetable = { navController.navigate(Timetable) },
            onNavigateToHandbookCreate = { navController.navigate(HandbookCreate) },
            onNavigateToHandbook = { navController.navigate(Handbook) },
            onNavigateToGPA = { navController.navigate(GPA) },
            onNavigateToNotes = { navController.navigate(Notes) }
        )
    }

    composable<GPA> {
        GPAPage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() }
        )
    }

    composable<Notes> {
        AllNotesPage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() }
        )
    }

    composable<Timetable> {
        TimetablePage(
            viewModel = viewModel,
            onUploadTimetable = { navController.navigate(TimetableUpload) },
            onEditTimetable = {
                val educationLevel = EducationLevel.fromJson(viewModel.appData.value.timetable?.educationLevel)
                    ?: EducationLevel.UNIVERSITY
                navController.navigate(TimetableManual(educationLevel, isEditMode = true))
            },
            onBack = { navController.popBackStack() }
        )
    }

    composable<StudyHistory> {
        StudyHistoryPage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() }
        )
    }

    composable<HandbookCreate> {
        HandbookCreatePage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onSave = {
                navController.navigate(AppGraph) {
                    popUpTo(HandbookCreate) { inclusive = true }
                }
            }
        )
    }

    composable<Handbook> {
        HandbookPage(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onLoad = {
                navController.navigate(AppGraph) {
                    popUpTo(Handbook) { inclusive = true }
                }
            },
            onEdit = { navController.navigate(HandbookEdit) }
        )
    }

    composable<HandbookEdit> {
        HandbookCreatePage(
            viewModel = viewModel,
            isEditMode = true,
            onBack = { navController.popBackStack() },
            onSave = { navController.popBackStack() }
        )
    }
}

fun NavGraphBuilder.appNavigation(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTimetableUpload: () -> Unit,
    onNavigateToStudyHistory: () -> Unit,
    onNavigateToTimetable: () -> Unit
) {
    composable<AppGraph> {
        val navController = rememberNavController()

        NavigationBottomBar(
            navController = navController
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Home,
                modifier = Modifier.padding(innerPadding)
            ) {

                composable<Home> {
                    HomePage(
                        viewModel = viewModel,
                        onUploadTimetable = onNavigateToTimetableUpload,
                        onSettingsClick = onNavigateToSettings,
                        onTimetableClick = onNavigateToTimetable,
                        onAddTaskForModule = { moduleCode -> navController.navigate(AddTask(moduleCode)) }
                    )
                }

                composable<AddTask> { backStackEntry ->
                    val args = backStackEntry.toRoute<AddTask>()

                    AddTaskPage(
                        viewModel = viewModel,
                        onUploadTimetable = onNavigateToTimetableUpload,
                        onAddTask = { navController.popBackStack() },
                        initialModuleCode = args.moduleCode
                    )
                }

                composable<Calendar> { CalendarPage(viewModel = viewModel) }

                composable<AllTasks> { AllTaskPage(viewModel = viewModel) }

                composable<StudySession> {
                    StudySessionPage(
                        viewModel = viewModel,
                        onOpenHistory =  onNavigateToStudyHistory
                    )
                }
            }

        }
    }
}