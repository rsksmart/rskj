package co.rsk.rpc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

/**
 * Created by ajlopez on 06/10/2017.
 */
public class OriginValidatorTest {
    @Test
    public void noOrigin() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("");

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void noOriginWithSpaces() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("  ");

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void nullOrigin() throws URISyntaxException {
        OriginValidator validator = new OriginValidator(null);

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void allOriginsUsingWildcard() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("*");

        Assertions.assertTrue(validator.isValidOrigin("http://localhost"));
        Assertions.assertTrue(validator.isValidOrigin("https://rks.co"));

        Assertions.assertTrue(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertTrue(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void allowLocalhost() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assertions.assertTrue(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assertions.assertTrue(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void invalidRefererWithDifferentProtocol() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assertions.assertFalse(validator.isValidReferer("https://localhost/index.html"));
    }

    @Test
    public void invalidRefererWithDifferentHost() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assertions.assertFalse(validator.isValidReferer("http://rsk.co/index.html"));
    }

    @Test
    public void invalidRefererWithDifferentPort() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assertions.assertFalse(validator.isValidReferer("http://localhost:3000/index.html"));
    }

    @Test
    public void allowDomain() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("https://rsk.co");

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertTrue(validator.isValidOrigin("https://rsk.co"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertTrue(validator.isValidReferer("https://rsk.co/index.html"));
    }

    @Test
    public void allowTwoDomains() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("https://rsk.co https://rsk.com.ar");

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertTrue(validator.isValidOrigin("https://rsk.co"));
        Assertions.assertTrue(validator.isValidOrigin("https://rsk.com.ar"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertTrue(validator.isValidReferer("https://rsk.co/index.html"));
        Assertions.assertTrue(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }

    @Test
    public void invalidUriInCreation() {
        OriginValidator validator = new OriginValidator("//");

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rsk.co"));
        Assertions.assertFalse(validator.isValidOrigin("https://rsk.com.ar"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rsk.co/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }

    @Test
    public void noReferer() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("");

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rsk.co"));
        Assertions.assertFalse(validator.isValidOrigin("https://rsk.com.ar"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rsk.co/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }

    @Test
    public void defaultValidator() throws URISyntaxException {
        OriginValidator validator = new OriginValidator();

        Assertions.assertFalse(validator.isValidOrigin("http://localhost"));
        Assertions.assertFalse(validator.isValidOrigin("https://rsk.co"));
        Assertions.assertFalse(validator.isValidOrigin("https://rsk.com.ar"));

        Assertions.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rsk.co/index.html"));
        Assertions.assertFalse(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }
}
