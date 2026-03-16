package uk.nhs.prm.repo.re_registration.utility;

import java.time.Instant;
import java.util.UUID;

public final class TestDataUtility {
    public static String UNESCAPED_HTML = "<!DOCTYPE html><html lang='en'><head></head><body></body></html>";

    public static String NHS_NUMBER = "9745812541";

    public static String getRandomTimestamp() {
        return Instant.now().toString();
    }

    public static String getRandomOdsCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private TestDataUtility() { }
}
