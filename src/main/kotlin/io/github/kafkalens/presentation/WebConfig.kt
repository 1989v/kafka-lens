package io.github.kafkalens.presentation

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

// Serves the bundled SPA. Static assets resolve to their real files under
// classpath:/static/; any path that doesn't match a real file or a REST
// controller falls back to index.html, so React Router can take it from
// there. /api and /sse are handled by their own controllers and never
// reach this resolver (RequestMappingHandlerMapping wins first).
//
// We use line comments here intentionally — Kotlin nests block comments,
// so wildcard globs like /api/star-star inside a /** ... */ block would
// open nested comments and break the outer doc.
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    val requested = location.createRelative(resourcePath)
                    return if (requested.exists() && requested.isReadable) requested
                    else location.createRelative("index.html").takeIf { it.exists() && it.isReadable }
                }
            })
    }
}
