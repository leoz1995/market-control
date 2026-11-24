package marketcontrol.http.server.infrastructure;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public final class TlsConfiguration {
    private TlsConfiguration() {}

    public static SSLContext load() {
        String keyStorePath = setting("MARKETCONTROL_TLS_KEYSTORE", "marketcontrol.tls.keystore");
        String password = setting("MARKETCONTROL_TLS_KEYSTORE_PASSWORD", "marketcontrol.tls.keystore-password");
        boolean required = Boolean.parseBoolean(setting("MARKETCONTROL_REQUIRE_HTTPS", "marketcontrol.require-https"))
                || "production".equalsIgnoreCase(setting("MARKETCONTROL_ENV", "marketcontrol.environment"));
        if ((keyStorePath == null) != (password == null))
            throw new IllegalStateException("Configure MARKETCONTROL_TLS_KEYSTORE e MARKETCONTROL_TLS_KEYSTORE_PASSWORD juntos.");
        if (keyStorePath == null) {
            if (required) throw new IllegalStateException("HTTPS obrigatório: configure o keystore e a senha via ambiente.");
            return null;
        }
        char[] passwordChars = password.toCharArray();
        try (InputStream in = Files.newInputStream(Path.of(keyStorePath))) {
            String type = setting("MARKETCONTROL_TLS_KEYSTORE_TYPE", "marketcontrol.tls.keystore-type");
            KeyStore keyStore = KeyStore.getInstance(type == null ? "PKCS12" : type);
            keyStore.load(in, passwordChars);
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, passwordChars);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), null, null);
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível iniciar HTTPS com o keystore configurado.", e);
        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
        }
    }

    private static String setting(String envName, String propertyName) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) value = System.getProperty(propertyName);
        return value == null || value.isBlank() ? null : value;
    }
}
