package marketcontrol.service;

import java.util.UUID;

import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.exception.ConflictException;
import marketcontrol.exception.ResourceNotFoundException;

public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        if(supplierRepository == null)
            throw new IllegalArgumentException("O repositório não Instânciado.");

        this.supplierRepository = supplierRepository;
    }

    public Supplier save(String legalName, String tradeName, String document, String email, String phone, String contactName) {

        if(document != null && existsByDocument(document))
            throw new ConflictException("O documento enviado já está em uso por outro fornecedor.");

        Supplier supplier = Supplier.create(legalName, tradeName, document, email, phone, contactName);
        supplierRepository.save(supplier);

        return supplier;
    }

    public ReadOnlySupplier findById(UUID uuid) {
        if (uuid == null) throw new IllegalArgumentException("O ID do fornecedor é obrigatório.");
        return supplierRepository.findById(uuid).orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado."));
    }

    public ReadOnlySupplier update(UUID id, String legalName, String tradeName, String document, String email, String phone, String contactName) {
        Supplier supplier = requireExistingSupplier(id);
        String currentDocument = supplier.getDocument();

        supplier.edit(legalName, tradeName, document, email, phone, contactName);
        if(!currentDocument.equals(supplier.getDocument()) && supplierRepository.existsByDocument(supplier.getDocument()))
            throw new ConflictException("O documento enviado já está em uso por outro fornecedor.");

        supplierRepository.update(supplier);
        return supplier;
    }

    public ReadOnlySupplier deactivate(UUID id) {
        Supplier supplier = requireExistingSupplier(id);
        supplier.setActive(false);
        supplierRepository.update(supplier);
        return supplier;
    }

    private Supplier requireExistingSupplier(UUID id) {
        if(id == null)
            throw new IllegalArgumentException("O ID do fornecedor é obrigatório.");

        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado."));
    }

    public boolean existsByDocument(String document) {
        if(document == null || document.isBlank())
            throw new IllegalArgumentException("O documento precisa ser enviado para a consulta.");

        return supplierRepository.existsByDocument(document);
    }
}
