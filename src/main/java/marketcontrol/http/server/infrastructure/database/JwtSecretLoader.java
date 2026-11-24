package marketcontrol.http.server.infrastructure.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class JwtSecretLoader {
    public static final String CONFIG = loadSecret();

    private JwtSecretLoader() {}

    private static String loadSecret() {
        String secret = setting("MARKETCONTROL_JWT_SECRET", "marketcontrol.jwt.secret");
        boolean production = "production".equalsIgnoreCase(setting("MARKETCONTROL_ENV", "marketcontrol.environment"));
        if (secret == null && production)
            throw new IllegalStateException("Em produção, configure MARKETCONTROL_JWT_SECRET fora dos arquivos da aplicação.");
        if (secret == null) {
            Properties properties = new Properties();
            try (InputStream input = JwtSecretLoader.class.getClassLoader().getResourceAsStream("database.properties")) {
                if (input == null) throw new IllegalStateException("database.properties não encontrado.");
                properties.load(input);
                secret = properties.getProperty("jwt.secret");
            } catch (IOException e) {
                throw new IllegalStateException("Erro ao carregar database.properties.", e);
            }
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.contains("SUBSTITUA_POR_"))
            throw new IllegalStateException("Configure um segredo JWT aleatório com pelo menos 32 bytes.");
        return secret;
    }

    private static String setting(String envName, String propertyName) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) value = System.getProperty(propertyName);
        return value == null || value.isBlank() ? null : value;
    }
}
