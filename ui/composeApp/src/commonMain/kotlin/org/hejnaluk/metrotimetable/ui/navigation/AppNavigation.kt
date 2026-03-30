package org.hejnaluk.metrotimetable.ui.navigation

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import org.hejnaluk.metrotimetable.ui.presentation.departures.DeparturesScreen
import org.hejnaluk.metrotimetable.ui.presentation.direction.DirectionScreen
import org.hejnaluk.metrotimetable.ui.presentation.line.LineScreen
import org.hejnaluk.metrotimetable.ui.presentation.station.StationScreen

@Serializable
private object LineRoute

@Serializable
private data class StationRoute(val lineId: String)

@Serializable
private data class DirectionRoute(val lineId: String, val station: String)

@Serializable
private data class DeparturesRoute(
    val lineId: String,
    val station: String,
    val directionId: Int,
    val destination: String
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = LineRoute) {
        composable<LineRoute> {
            LineScreen(
                onLineSelected = { lineId ->
                    navController.navigate(StationRoute(lineId))
                }
            )
        }
        composable<StationRoute> { backStackEntry ->
            val route: StationRoute = backStackEntry.toRoute()
            StationScreen(
                lineId = route.lineId,
                onStationSelected = { station ->
                    navController.navigate(DirectionRoute(route.lineId, station))
                }
            )
        }
        composable<DirectionRoute> { backStackEntry ->
            val route: DirectionRoute = backStackEntry.toRoute()
            DirectionScreen(
                lineId = route.lineId,
                station = route.station,
                onDirectionSelected = { directionId, destination ->
                    navController.navigate(
                        DeparturesRoute(route.lineId, route.station, directionId, destination)
                    )
                }
            )
        }
        composable<DeparturesRoute> { backStackEntry ->
            val route: DeparturesRoute = backStackEntry.toRoute()
            DeparturesScreen(
                station = route.station,
                directionId = route.directionId,
                destination = route.destination,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
