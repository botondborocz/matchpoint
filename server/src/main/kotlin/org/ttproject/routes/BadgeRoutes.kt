package org.ttproject.routes

import org.ttproject.data.UserBadgeMetricsDto
import org.ttproject.services.BadgeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.badgeRouting(badgeService: BadgeService) {
    // Assuming you are using JWT authentication
    authenticate("auth-jwt") {
        route("/api/profile/badges") {

            get {
                // Extract user ID from the JWT token
                val principal = call.principal<JWTPrincipal>()
                val userIdStr = principal?.payload?.getClaim("userId")?.asString()

                if (userIdStr == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                    return@get
                }

                val userId = UUID.fromString(userIdStr)
                val metrics = badgeService.getMetricsForUser(userId)

                if (metrics != null) {
                    call.respond(HttpStatusCode.OK, metrics)
                } else {
                    // If metrics don't exist for some reason, return all 0s
                    call.respond(HttpStatusCode.OK,
                        UserBadgeMetricsDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                    )
                }
            }
        }
    }
}