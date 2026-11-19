package marketcontrol.domain.model;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.exception.data.DataMappingException;

public class Supplier implements ReadOnlySupplier{

    private final UUID id;
    private String legalName;
    private String tradeName;
    private String document;
    private String email;
    private String phone;
    private String contactName;
    private boolean active;
    private final LocalDateTime createdAt;

    private Supplier(UUID id, String legalName, String tradeName, String document, String email, String phone, String contactName, boolean active, LocalDateTime createdAt){
        this.id = id;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.contactName = contactName;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static Supplier create(String legalName, String tradeName, String document, String email, String phone, String contactName) {
        validateDetails(legalName, tradeName, document, email, phone, contactName);
        String documentValue = document.trim();

        return new Supplier(UUID.randomUUID(), legalName.trim().toLowerCase(Locale.ROOT), tradeName.trim().toLowerCase(Locale.ROOT), documentValue.toLowerCase(Locale.ROOT), email.trim().toLowerCase(Locale.ROOT), phone.trim(), contactName.trim().toLowerCase(Locale.ROOT), true, LocalDateTime.now());
    }

    public static Supplier reconstitute(UUID id, String legalName, String tradeName, String document, String email, String phone, String contactName, boolean active, LocalDateTime createdAt) {

        if(id == null)
            throw new DataMappingException("O ID do fornecedor persistido é obrigatório.");

        if(legalName == null || legalName.isBlank())
            throw new DataMappingException("O nome do fornecedor persistido é obrigatório.");

        if(document == null || document.isBlank())
            throw new DataMappingException("O documento do fornecedor persistido é obrigatório.");

        if(createdAt == null)
            throw new DataMappingException("A data de cadastro do fornecedor é obrigatório na persistência.");

        return new Supplier(id, legalName.trim(), tradeName, document, email, phone, contactName, active, createdAt);
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getContactName() {
        return contactName;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void edit(String legalName, String tradeName, String document, String email, String phone, String contactName) {
        validateDetails(legalName, tradeName, document, email, phone, contactName);
        String documentValue = document.trim();

        this.legalName = legalName.trim().toLowerCase(Locale.ROOT);
        this.tradeName = tradeName.trim().toLowerCase(Locale.ROOT);
        this.document = documentValue.toLowerCase(Locale.ROOT);
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phone = phone.trim();
        this.contactName = contactName.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateDetails(String legalName, String tradeName, String document, String email, String phone, String contactName) {
        if(legalName == null || legalName.isBlank())
            throw new IllegalArgumentException("O nome do fornecedor é obrigatório no cadastro.");

        if(document == null || document.isBlank())
            throw new IllegalArgumentException("O documento do fornecedor é obrigatório no cadastro.");

        String documentValue = document.trim();
        if(documentValue.length() != 14)
            throw new IllegalArgumentException("Documento da empresa incompleto.");

        if(!documentValue.chars().allMatch(Character::isDigit))
            throw new IllegalArgumentException("A documentação da empresa precisa ser numérica, está incorreta!");

        if(email == null || email.isBlank())
            throw new IllegalArgumentException("O email da empresa é obrigatório no cadastro!");

        if(phone == null || phone.isBlank())
            throw new IllegalArgumentException("Telefone inválido.");

        if(tradeName == null || tradeName.isBlank())
            throw new IllegalArgumentException("Nome da empresa inválido.");

        if(contactName == null || contactName.isBlank())
            throw new IllegalArgumentException("Nome de contato inválido.");
    }

    public boolean isActive() {
        return active;
    }

    public UUID getId() {
        return id;
    }

    public String getDocument() {
        return document;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
