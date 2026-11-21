package io.github.oliverjuhasz.boardkeeper.about.internal;

import io.github.oliverjuhasz.boardkeeper.about.RunningVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes the running version available to the other modules, worked out as for the About page. */
@Configuration
class RunningVersionConfiguration {

    @Bean
    RunningVersion runningVersion(ObjectProvider<GitProperties> git) {
        Build build = Build.of(git.getIfAvailable());
        return new RunningVersion(build.version(), build.commit());
    }
}
