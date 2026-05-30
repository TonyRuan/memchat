package com.memorychat.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memorychat.app.ui.chat.ChatScreen
import com.memorychat.app.ui.chat.ConversationListScreen
import com.memorychat.app.ui.memory.MemoryCenterScreen
import com.memorychat.app.ui.settings.SettingsScreen
import com.memorychat.app.ui.debug.DebugScreen
import com.memorychat.app.ui.debug.LogScreen

@Composable
fun MainNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "conversations") {
        composable("conversations") {
            ConversationListScreen(
                onNavigateToChat = { convId -> navController.navigate("chat/$convId") },
                onNavigateToMemory = { navController.navigate("memory") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToLogs = { navController.navigate("logs") }
            )
        }
        composable(
            "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = convId,
                onBack = { navController.popBackStack() },
                onNavigateToDebug = { navController.navigate("debug/$convId") }
            )
        }
        composable("memory") {
            MemoryCenterScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("logs") {
            LogScreen(onBack = { navController.popBackStack() })
        }
        composable(
            "debug/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            DebugScreen(conversationId = convId, onBack = { navController.popBackStack() })
        }
    }
}
