package marketcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class JUnitConfigurationTest {

    @Test
    void shouldExecuteJUnitTest() {
        int result = 10 + 20;

        assertEquals(30, result);
    }
}
