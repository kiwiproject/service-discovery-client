package org.kiwiproject.registry.consul.util;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.pszymczyk.consul.ConsulStarter;
import com.pszymczyk.consul.ConsulStarterBuilder;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@UtilityClass
@Slf4j
public class ConsulStarterHelper {

    private static final String CONSUL_DOWNLOAD_LOCATION_SYSTEM_PROPERTY = "embedded.consul.downloadDir";

    /**
     * This will provide a ConsulStarter object populated with values from system properties if available.
     * <p>
     * Note: We should either move this to kiwi-test or contribute it to the embedded-postgres library
     *
     * @return the built ConsulStarter
     */
    public static ConsulStarter buildStarterConfigWithEnvironment() {
        var starter = ConsulStarterBuilder.consulStarter();

        var downloadLocation = System.getProperty(CONSUL_DOWNLOAD_LOCATION_SYSTEM_PROPERTY);
        if (isNotBlank(downloadLocation)) {
            var downloadPath = Path.of(downloadLocation);

            try {
                if (!Files.exists(downloadPath)) {
                    Files.createDirectory(downloadPath);
                }
                starter.withConsulBinaryDownloadDirectory(downloadPath);
            } catch (IOException e) {
                LOG.error("Unable to create consul download directory going to use the default", e);
            }
        }

        return starter.build();
    }
}
