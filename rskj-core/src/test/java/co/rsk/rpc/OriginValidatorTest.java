package co.rsk.rpc;

import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;

/**
 * Created by ajlopez on 06/10/2017.
 */
public class OriginValidatorTest {
    @Test
    public void noOrigin() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("");

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void noOriginWithSpaces() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("  ");

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void nullOrigin() throws URISyntaxException {
        OriginValidator validator = new OriginValidator(null);

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void allOriginsUsingWildcard() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("*");

        Assert.assertTrue(validator.isValidOrigin("http://localhost"));
        Assert.assertTrue(validator.isValidOrigin("https://rks.co"));

        Assert.assertTrue(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertTrue(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void allowLocalhost() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assert.assertTrue(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rks.co"));

        Assert.assertTrue(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rks.co/index.html"));
    }

    @Test
    public void invalidRefererWithDifferentProtocol() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assert.assertFalse(validator.isValidReferer("https://localhost/index.html"));
    }

    @Test
    public void invalidRefererWithDifferentHost() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assert.assertFalse(validator.isValidReferer("http://rsk.co/index.html"));
    }

    @Test
    public void invalidRefererWithDifferentPort() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("http://localhost");

        Assert.assertFalse(validator.isValidReferer("http://localhost:3000/index.html"));
    }

    @Test
    public void allowDomain() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("https://rsk.co");

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertTrue(validator.isValidOrigin("https://rsk.co"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertTrue(validator.isValidReferer("https://rsk.co/index.html"));
    }

    @Test()
    public void allowTwoDomains() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("https://rsk.co https://rsk.com.ar");

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertTrue(validator.isValidOrigin("https://rsk.co"));
        Assert.assertTrue(validator.isValidOrigin("https://rsk.com.ar"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertTrue(validator.isValidReferer("https://rsk.co/index.html"));
        Assert.assertTrue(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }

    @Test
    public void invalidUriInCreation() {
        OriginValidator validator = new OriginValidator("//");

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rsk.co"));
        Assert.assertFalse(validator.isValidOrigin("https://rsk.com.ar"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rsk.co/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }

    @Test
    public void noReferer() throws URISyntaxException {
        OriginValidator validator = new OriginValidator("");

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rsk.co"));
        Assert.assertFalse(validator.isValidOrigin("https://rsk.com.ar"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rsk.co/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }

    @Test
    public void defaultValidator() throws URISyntaxException {
        OriginValidator validator = new OriginValidator();

        Assert.assertFalse(validator.isValidOrigin("http://localhost"));
        Assert.assertFalse(validator.isValidOrigin("https://rsk.co"));
        Assert.assertFalse(validator.isValidOrigin("https://rsk.com.ar"));

        Assert.assertFalse(validator.isValidReferer("http://localhost/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rsk.co/index.html"));
        Assert.assertFalse(validator.isValidReferer("https://rsk.com.ar/index.html"));
    }
}
