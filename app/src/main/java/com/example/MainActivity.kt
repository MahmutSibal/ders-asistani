package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppBottomBar
import com.example.ui.components.AppTopBar
import com.example.ui.navigation.Screen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val studyViewModel: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                StudyAssistantApp(
                    authViewModel = authViewModel,
                    studyViewModel = studyViewModel
                )
            }
        }
    }
}

@Composable
fun StudyAssistantApp(
    authViewModel: AuthViewModel,
    studyViewModel: StudyViewModel
) {
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val studyUiState by studyViewModel.uiState.collectAsStateWithLifecycle()
    val courses by studyViewModel.courses.collectAsStateWithLifecycle()
    val notes by studyViewModel.allNotes.collectAsStateWithLifecycle()
    val quizAttempts by studyViewModel.allQuizAttempts.collectAsStateWithLifecycle()
    val chatMessages by studyViewModel.chatMessages.collectAsStateWithLifecycle()
    val averageScore by studyViewModel.averageScore.collectAsStateWithLifecycle()
    val totalQuizCount by studyViewModel.totalQuizCount.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show status messages in snackbar
    LaunchedEffect(studyUiState.statusMessage) {
        studyUiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            studyViewModel.clearStatusMessage()
        }
    }

    if (!authUiState.isLoggedIn) {
        LoginScreen(
            uiState = authUiState,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onLoginClick = authViewModel::login,
            onDemoLoginClick = authViewModel::quickDemoLogin
        )
    } else {
        // Back button navigation
        BackHandler(enabled = currentScreen != Screen.HOME) {
            currentScreen = Screen.HOME
        }

        Scaffold(
            topBar = {
                AppTopBar(
                    title = currentScreen.title,
                    subtitle = if (currentScreen == Screen.HOME) {
                        "Akıllı Üniversite Asistanı"
                    } else {
                        courses.find { it.id == studyUiState.selectedCourseId }?.let { "${it.code} - ${it.name}" }
                    },
                    connectionMode = studyUiState.serverConfig.mode,
                    modelName = studyUiState.serverConfig.selectedModel,
                    onStatusClick = {
                        currentScreen = Screen.SETTINGS
                    }
                )
            },
            bottomBar = {
                AppBottomBar(
                    currentScreen = currentScreen,
                    onScreenSelected = { screen ->
                        currentScreen = screen
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    Screen.HOME -> {
                        HomeScreen(
                            user = authUiState.currentUser,
                            uiState = studyUiState,
                            courses = courses,
                            notes = notes,
                            quizAttempts = quizAttempts,
                            averageScore = averageScore,
                            totalQuizCount = totalQuizCount,
                            onNavigate = { screen -> currentScreen = screen },
                            onSelectCourse = { courseId ->
                                studyViewModel.selectCourse(courseId)
                            }
                        )
                    }

                    Screen.CHAT -> {
                        ChatScreen(
                            uiState = studyUiState,
                            courses = courses,
                            chatMessages = chatMessages,
                            onSelectCourse = { courseId ->
                                studyViewModel.selectCourse(courseId)
                            },
                            onSendMessage = { question ->
                                studyViewModel.sendChatMessage(question)
                            },
                            onClearChat = studyViewModel::clearChat
                        )
                    }

                    Screen.NOTES -> {
                        NotesScreen(
                            uiState = studyUiState,
                            courses = courses,
                            notes = notes,
                            onSelectCourse = { courseId ->
                                studyViewModel.selectCourse(courseId)
                            },
                            onTitleChange = studyViewModel::onNoteTitleChange,
                            onContentChange = studyViewModel::onNoteContentChange,
                            onLoadTemplate = studyViewModel::loadTemplateNote,
                            onSummarize = studyViewModel::summarizeCurrentNote,
                            onGenerateQuiz = {
                                studyViewModel.generateQuizFromCurrentNote(
                                    onNavigateToQuiz = {
                                        currentScreen = Screen.QUIZ
                                    }
                                )
                            },
                            onSaveNote = studyViewModel::saveCurrentNote,
                            onDeleteNote = { noteId ->
                                studyViewModel.deleteNote(noteId)
                            },
                            onOpenNote = { note ->
                                studyViewModel.openExistingNote(note)
                            }
                        )
                    }

                    Screen.QUIZ -> {
                        QuizScreen(
                            uiState = studyUiState,
                            courses = courses,
                            onSelectAnswer = { qIndex, opt ->
                                studyViewModel.selectQuizAnswer(qIndex, opt)
                            },
                            onNextQuestion = studyViewModel::nextQuizQuestion,
                            onPreviousQuestion = studyViewModel::previousQuizQuestion,
                            onSubmitQuiz = studyViewModel::submitQuiz,
                            onRestartQuiz = studyViewModel::restartQuiz,
                            onStartNewQuiz = {
                                studyViewModel.generateQuizFromCurrentNote(
                                    onNavigateToQuiz = {}
                                )
                            }
                        )
                    }

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            user = authUiState.currentUser,
                            uiState = studyUiState,
                            onUpdateConfig = studyViewModel::updateServerConfig,
                            onTestConnection = studyViewModel::testConnection,
                            onLogout = authViewModel::logout
                        )
                    }
                }
            }
        }
    }
}
