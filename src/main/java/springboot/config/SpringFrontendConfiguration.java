package springboot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the existing Vite build without changing the current route handlers. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpringFrontendConfiguration implements WebMvcConfigurer {
    private final FrontendProperties frontendProperties;

    public SpringFrontendConfiguration(FrontendProperties frontendProperties) {
        this.frontendProperties = frontendProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var location = frontendProperties.distPath().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCachePeriod(0);
    }
}
