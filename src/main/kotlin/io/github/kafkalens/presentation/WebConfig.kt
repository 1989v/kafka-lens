package io.github.kafkalens.presentation

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
        registry.addResourceHandler("/favicon.svg", "/favicon.ico", "/manifest.webmanifest")
            .addResourceLocations("classpath:/static/")
    }
}
