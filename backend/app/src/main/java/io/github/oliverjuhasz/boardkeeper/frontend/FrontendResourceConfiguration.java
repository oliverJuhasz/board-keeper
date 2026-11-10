package io.github.oliverjuhasz.boardkeeper.frontend;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration(proxyBeanMethods = false)
class FrontendResourceConfiguration implements WebMvcConfigurer {

    private static final String FRONTEND = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a hash of each file's content in its name, so these never change: cache them for good.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(FRONTEND + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // Everything else, index.html above all, is checked with the server on every use,
        // so a new release is picked up straight away.
        registry.addResourceHandler("/**")
                .addResourceLocations(FRONTEND)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new FrontendResourceResolver());
    }

    /**
     * Serves {@code index.html} for addresses that are pages of the frontend. The frontend is a
     * single-page app: an address such as {@code /games/42} is not a file on the server but a
     * route that React Router handles in the browser, once {@code index.html} has loaded.
     */
    static class FrontendResourceResolver extends PathResourceResolver {

        private static final List<String> NOT_FRONTEND = List.of("api", "actuator", "v3", "swagger-ui");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource file = super.getResource(resourcePath, location);
            if (file != null) {
                return file;
            }
            return isFrontendRoute(resourcePath) ? super.getResource("index.html", location) : null;
        }

        static boolean isFrontendRoute(String path) {
            String firstSegment = path.split("/", 2)[0];
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            // A dot in the last segment means a file was asked for; a missing file stays a 404.
            return !NOT_FRONTEND.contains(firstSegment) && !lastSegment.contains(".");
        }
    }
}
