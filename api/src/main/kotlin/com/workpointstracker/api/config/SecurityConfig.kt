package com.workpointstracker.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(1)
class ApiKeyFilter(
    @Value("\${app.api-key}") private val apiKey: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Allow H2 console and health checks without auth
        val path = request.requestURI
        if (path.startsWith("/h2-console") || path == "/api/health") {
            filterChain.doFilter(request, response)
            return
        }

        // Check API key for all /api/ endpoints
        if (path.startsWith("/api/")) {
            val providedKey = request.getHeader("X-API-Key")
            if (providedKey == null || providedKey != apiKey) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "application/json"
                response.writer.write("""{"error": "Invalid or missing API key"}""")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}

@Configuration
class WebConfig {
    @Bean
    fun corsFilter(): org.springframework.web.filter.CorsFilter {
        val source = org.springframework.web.cors.UrlBasedCorsConfigurationSource()
        val config = org.springframework.web.cors.CorsConfiguration()
        config.allowedOriginPatterns = listOf("*")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        source.registerCorsConfiguration("/**", config)
        return org.springframework.web.filter.CorsFilter(source)
    }
}
