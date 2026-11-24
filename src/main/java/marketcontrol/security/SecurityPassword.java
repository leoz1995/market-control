package marketcontrol.security;

import org.mindrot.jbcrypt.BCrypt;

public class SecurityPassword {

	public String encode(String password) {
		return BCrypt.hashpw(password, BCrypt.gensalt(12));
	}

	public boolean matches(String password, String hash) {
		return BCrypt.checkpw(password, hash);
	}
}
