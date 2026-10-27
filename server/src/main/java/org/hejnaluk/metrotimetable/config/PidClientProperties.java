package org.hejnaluk.metrotimetable.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@ConfigurationProperties(prefix = "pid.client")
@Validated
@Getter
@Setter
public class PidClientProperties {

    private String path = "";

    @NotBlank(message = "pid.client.root.path must not be blank")
    private String rootPath = "/tmp/timetable/";

    private int daysOffset = 7;

    private Set<String> routeIds = Set.of();

    @Getter
    @Setter
    public static class Station {
        private int maxLimit = 15;
    }

    private Station station = new Station();
}
