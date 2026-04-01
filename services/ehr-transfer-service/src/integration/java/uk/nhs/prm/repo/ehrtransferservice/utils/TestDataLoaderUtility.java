package uk.nhs.prm.repo.ehrtransferservice.utils;

import org.springframework.util.ResourceUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class TestDataLoaderUtility {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestDataLoaderUtility() { }

    public static String getTestDataAsString(String fileName) throws IOException {
        File file = ResourceUtils.getFile(String.format("classpath:data/%s", fileName));
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }
}
