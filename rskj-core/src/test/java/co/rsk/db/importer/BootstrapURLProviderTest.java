package co.rsk.db.importer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

public class BootstrapURLProviderTest {

    @Test
    public void getFullURL() {
        String BASE_URL = "http://localhost/baseURL";
        BootstrapURLProvider bootstrapURLProvider = new BootstrapURLProvider(BASE_URL);
        String suffix = "suffix";
        URL fullURL = bootstrapURLProvider.getFullURL(suffix);
        try {
            URL expected = new URL(BASE_URL + suffix);
            assertEquals(expected, fullURL);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getWrongFullURL() {
        String BASE_URL = "localhost/baseURL";
        BootstrapURLProvider bootstrapURLProvider = new BootstrapURLProvider(BASE_URL);
        String suffix = "suffix";

        Assertions.assertThrows(BootstrapImportException.class, () -> bootstrapURLProvider.getFullURL(suffix));
    }

}
