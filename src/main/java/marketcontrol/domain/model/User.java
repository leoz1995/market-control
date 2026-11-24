package marketcontrol.domain.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import marketcontrol.domain.model.enums.Role;

public class User{
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

	private UUID id;
	private String name;
	private String email;
	private String password;
	private LocalDateTime updateTime;
	private LocalDateTime entryDate;
	private Role role;

	private User(UUID id, String name, String email, String password, Role role){
		this.id = id;
		this.name = name;
		this.email = email;
		this.password = password;
		this.entryDate = LocalDateTime.now();
		this.role = role;
	}

	private User(UUID id, String name, String email, String password, LocalDateTime updateTime, LocalDateTime entryDate, Role role){
		this.id = id;
		this.name = name;
		this.email = email;
		this.password = password;
		this.updateTime = updateTime;
		this.entryDate = entryDate;
		this.role = role;
	}

	public static User create(String name, String email, String hash) {
		return create(name, email, hash, Role.INACTIVE);
	}

	public static User create(String name, String email, String hash, Role role) {
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("Nome de usuário é obrigatório.");

		if(email == null || email.isBlank())
			throw new IllegalArgumentException("Nome de email é obrigatório.");

		if(hash == null || hash.isBlank())
			throw new IllegalArgumentException("A senha da conta é obrigatório.");
		if(role == null)
			throw new IllegalArgumentException("O papel da conta é obrigatório.");

		return new User(UUID.randomUUID(), name.trim(), email.trim(), hash, role);
	}

	public static User reconstitute(UUID id, String name, String email, String password, LocalDateTime updateTime, LocalDateTime entryDate, Role type) {
		if(id == null)
			throw new IllegalArgumentException("O id do usuário reconstruído é inválido.");

		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome  do usuário reconstruído é inválido.");

		if(email == null || email.isBlank())
			throw new IllegalArgumentException("O email do usuário reconstruído é inválido.");

		if(password == null)
			throw new IllegalArgumentException("A senha do usuário reconstruído é inválida.");

		if(entryDate == null)
			throw new IllegalArgumentException("A data de entrada do usuário é inválida.");

		if(type == null)
			throw new IllegalArgumentException("A hierarquia do usuário é inválida.");

		return new User(id, name, email, password, updateTime, entryDate, type);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public LocalDateTime getUpdateInfo() {
		return updateTime;
	}

	public LocalDateTime getEntryDate() {
		return entryDate;
	}

	public Role getType() {
		return role;
	}

	public String getEntryFormattedDate() {
		return entryDate != null ? entryDate.format(formatter) : null;
	}
}
