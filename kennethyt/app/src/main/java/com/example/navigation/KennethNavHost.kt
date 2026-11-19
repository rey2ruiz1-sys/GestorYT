package com.example.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.domain.model.MediaType
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.session.InterviewSessionScreen
import com.example.ui.session.InterviewSessionViewModel
import com.example.ui.session.SessionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class ViewerRoute(
    val interviewId: Long,
    val initialMode: String = SessionMode.VIEWER.name,
    val initialMediaType: String? = null,
    val initialVideoPosMs: Long = 0L,
    val initialQuestionIndex: Int = 0,
    val initialQuestionOffset: Int = 0
)

@Serializable
data class EditorRoute(val interviewId: Long)

@Composable
fun KennethNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val scope = rememberCoroutineScope()
    var hasAttemptedColdRestore by rememberSaveable { mutableStateOf(false) }

    // Cold-start session recovery: restores active interview, mode (Viewer/Interview),
    // selected media tab, paused video position, and question scroll position if still valid.
    LaunchedEffect(Unit) {
        if (!hasAttemptedColdRestore) {
            hasAttemptedColdRestore = true
            val targetSession = withContext(Dispatchers.IO) {
                val savedSession = container.preferencesRepository.persistedSessionState.first()
                    ?: return@withContext null
                container.interviewRepository.ensureSeeded()
                val existingInterviews = container.interviewRepository.interviews.first()
                if (existingInterviews.any { it.id == savedSession.interviewId }) {
                    savedSession
                } else {
                    container.preferencesRepository.clearActiveSession()
                    null
                }
            }
            if (targetSession != null) {
                withContext(Dispatchers.Main.immediate) {
                    navController.navigate(
                        ViewerRoute(
                            interviewId = targetSession.interviewId,
                            initialMode = targetSession.mode,
                            initialMediaType = targetSession.mediaType.name,
                            initialVideoPosMs = targetSession.videoPositionMs,
                            initialQuestionIndex = targetSession.questionScrollIndex,
                            initialQuestionOffset = targetSession.questionScrollOffset
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = { fadeIn(animationSpec = tween(180)) },
        exitTransition = { fadeOut(animationSpec = tween(140)) },
        popEnterTransition = { fadeIn(animationSpec = tween(180)) },
        popExitTransition = { fadeOut(animationSpec = tween(140)) }
    ) {
        composable<HomeRoute> {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(
                    repository = container.interviewRepository,
                    preferences = container.preferencesRepository,
                    backupTransferManager = container.backupTransferManager
                )
            )
            HomeScreen(
                viewModel = viewModel,
                onOpenViewer = { interviewId ->
                    navController.navigate(ViewerRoute(interviewId)) {
                        launchSingleTop = true
                    }
                },
                onOpenEditor = { interviewId ->
                    navController.navigate(EditorRoute(interviewId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ViewerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ViewerRoute>()
            val initialMode = runCatching { SessionMode.valueOf(route.initialMode) }
                .getOrDefault(SessionMode.VIEWER)
            val initialMediaType = route.initialMediaType?.let {
                runCatching { MediaType.valueOf(it) }.getOrNull()
            }
            val viewModel: InterviewSessionViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = InterviewSessionViewModel.provideFactory(
                    initialInterviewId = route.interviewId,
                    initialMode = initialMode,
                    initialMediaType = initialMediaType,
                    initialVideoPosMs = route.initialVideoPosMs,
                    initialQuestionIdx = route.initialQuestionIndex,
                    initialQuestionOffset = route.initialQuestionOffset,
                    repository = container.interviewRepository,
                    preferences = container.preferencesRepository
                )
            )
            InterviewSessionScreen(
                viewModel = viewModel,
                onExitToHome = {
                    navController.popBackStack(HomeRoute, inclusive = false)
                },
                onOpenEditor = { activeId ->
                    navController.navigate(EditorRoute(activeId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<EditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditorRoute>()
            val viewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.provideFactory(
                    interviewId = route.interviewId,
                    repository = container.interviewRepository
                )
            )
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenViewer = { id ->
                    navController.navigate(ViewerRoute(id)) {
                        popUpTo(HomeRoute)
                        launchSingleTop = true
                    }
                },
                onDeleted = {
                    scope.launch {
                        val saved = container.preferencesRepository.persistedSessionState.first()
                        if (saved?.interviewId == route.interviewId) {
                            container.preferencesRepository.clearActiveSession()
                        }
                    }
                    navController.popBackStack(HomeRoute, inclusive = false)
                },
                onOpenDuplicate = { duplicateId ->
                    navController.navigate(EditorRoute(duplicateId)) {
                        popUpTo(route) { inclusive = true }
                    }
                }
            )
        }
    }
}
