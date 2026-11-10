package marketcontrol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;
import marketcontrol.service.CategoryService;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;

public class Program {

	private static final CategoryService categoryService = new CategoryService();
	private static final ProductService productService = new ProductService(categoryService);
	private static final LotService lotService = new LotService(productService);

	public static void main(String[] args) {
		try (Scanner sc = new Scanner(System.in)){
			String option;
			System.out.println("MARKET CONTROLLER - BY LEONARDO ALVES");

			do {
				System.out.println("\nESCOLHA A FUNÇÃO QUE PRETENDE UTILIZAR: ");
				System.out.println("1 - CADASTRAR PRODUTOS");
				System.out.println("2 - LISTAR PRODUTOS");
				System.out.println("3 - BUSCAR PRODUTOS PELO CÓDIGO DE BARRAS");
				System.out.println("4 - EDITAR PRODUTOS");
				System.out.println("5 - CADASTRAR CATEGORIAS");
				System.out.println("6 - CADASTRAR LOTES");
				System.out.println("7 - CONSULTAR LOTES E ESTOQUE");
				System.out.println("8 - SAIR");

				option = sc.nextLine().trim();

				try {
					switch (option) {
					case "1":
						addProduct(sc);
						break;
					case "2":
						productList();
						break;
					case "3":
						System.out.println("Digite o código do produto: ");
						String barCode = sc.nextLine().toUpperCase();
						System.out.println(productService.searchBarCode(barCode));
						break;
					case "4":
						productEdit(sc);
						break;
					case "5":
						addCategory(sc);
						break;
					case "6":
						addLot(sc);
						break;
					case "7":
						consultLotAndStock(sc);
						break;
					case "8":
						System.out.println("Encerrando o MarketControl...");
						break;
					default:
						System.out.println("Opção inválida!");

					}
				}
				catch (IllegalArgumentException | IllegalStateException e){
					System.out.println("Não foi possível concluir a operação: "+e.getMessage());
				}
			} while (!option.equals("8"));
		}
	}

	private static void addProduct(Scanner sc) {
		System.out.println("Digite o nome do produto: ");
		String name = sc.nextLine();

		System.out.println("Digite o código de barras do produto: ");
		String code = sc.nextLine();

		System.out.println("Digite a descrição do produto: ");
		String desc = sc.nextLine();

		UnitOfMeasure unitValue = readUnitOfMeasure(sc);
		System.out.println("Unidade selecionada: " + unitValue);
		
		categoryService.getCategories().forEach(System.out::println);

		UUID catId = readCategoryId(sc);
		
		ReadOnlyProduct save = productService.saveProduct(name, code, desc, catId, unitValue);

		System.out.println("Produtos cadastrados: ");
		System.out.println(save.getName());
	}

	private static UnitOfMeasure readUnitOfMeasure(Scanner sc) {
		while(true) {
			System.out.println("Digite a Unidade do produto: ");
			System.out.println("1. Unidade | 2. Quilo | 3. Gramas | 4. Litros | 5. Mililitros");
			String unit = sc.nextLine().trim();

			switch (unit) {
		        case "1" -> { return UnitOfMeasure.UNIT; }
		        case "2" -> { return UnitOfMeasure.KILOGRAM; }
		        case "3" -> { return UnitOfMeasure.GRAM; }
		        case "4" -> { return UnitOfMeasure.LITER; }
		        case "5" -> { return UnitOfMeasure.MILLILITER; }
		        default -> System.out.println("Opção inválida! Por favor digite novamente.");
			}
		}
	}
	
	private static void productList() {
		List<ReadOnlyProduct> saved = productService.getProducts();
		if(saved.isEmpty())
		{
			System.out.println("Nenhum produto cadastrado.");
			return;
		}
		saved.forEach(System.out::println);
	}

	private static void productEdit(Scanner sc) {
		productList();
		System.out.println("Digite o codigo de barras do produto à ser editado: ");
		String barCode = sc.nextLine();
		
		ReadOnlyProduct p = productService.findByBarCode(barCode);
		
		System.out.println("Digite o nome do produto: ");
		String n = sc.nextLine();
		
		System.out.println("Digite o código de barras: ");
		String bc = sc.nextLine();

		System.out.println("Digite a descrição: ");
		String desc = sc.nextLine();
		
		categoryService.getCategories().forEach(System.out::println);
		UUID id = readCategoryId(sc);
		
		UnitOfMeasure currentUnit = p.getUnit();
		
		ProductDTO updated = new ProductDTO(n, bc, desc, id, currentUnit);
		productService.editProduct(p.getId(), updated);
		System.out.println("-------- ATUALIZADO --------");
		System.out.println(p);
	}
	
	private static void addCategory(Scanner sc) {
		System.out.println("Digite o nome da categoria: ");
		String name = sc.nextLine();

		System.out.println("Digite a descrição da categoria: ");
		String desc = sc.nextLine();

		ReadOnlyCategory save = categoryService.saveCategory(name, desc, true);

		System.out.println("Nova categoria salva: ");
		System.out.println(save.getName());
	}
	
	private static void addLot(Scanner sc) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);	
		productList();
		
		UUID id = readProductId(sc);
		
		LocalDate dataEntry = readEntryDate(sc, "Digite a DATA de entrada (dd/MM/uuuu): ", formatter);
		
		LocalDate dataExpires = readExpiresDate(sc, "Digite a DATA de validade (dd/MM/uuuu): ", formatter, dataEntry);
		BigDecimal total = readPositiveQuantity(sc);

		ReadOnlyLot save = lotService.save(id, dataEntry, dataExpires, total);

		System.out.println("Novo LOTE inserido: ");
		System.out.println(save.getId() + " | " + save.getEntryDate() + " | " + save.getExpirationDate() + " | "
				+ save.getInitialQuantity());
	}

	private static void consultLotAndStock(Scanner sc) {
		UUID id = readProductId(sc);
		
		List<ReadOnlyLot> lots = lotService.findByProductId(id);
		if(lots.isEmpty())
		{
			System.out.println("Este produto ainda não possui lotes cadastrados.");
			return;
		}
		
		System.out.println("Estoque disponível: "+lotService.getTotalAvailableQuantity(id));
		
		System.out.println("---------------------------------");
		lots.forEach(System.out::println);
		
		UUID lotId = readLotUUID(sc, "Insira o ID do lote: ", id);
		
		System.out.println(lotService.findById(lotId));
	}
	
	private static UUID readLotUUID(Scanner sc, String message, UUID productId) {
	    while (true) {
	        System.out.println(message);
	        String input = sc.nextLine().trim();

	        UUID id;

	        try {
	            id = UUID.fromString(input);

	            if (!id.toString().equalsIgnoreCase(input)) {
	                System.out.println("UUID inválido. Digite o ID completo.");
	                continue;
	            }
	        } catch (IllegalArgumentException e) {
	            System.out.println("ID inválido. Tente novamente.");
	            continue;
	        }

	        try {
	            ReadOnlyLot lot = lotService.findById(id);

	            if (!lot.getProduct().getId().equals(productId)) {
	                System.out.println("O lote não pertence ao produto selecionado.");
	                continue;
	            }

	            return id;

	        } catch (IllegalArgumentException e) {
	            System.out.println("Lote não encontrado. Tente novamente.");
	        }
	    }
	}
	
	private static UUID readUUID(Scanner sc, String message) {
		
		while(true) {
			System.out.println(message);
			String input = sc.nextLine().trim();
			
			try {
				UUID id = UUID.fromString(input);
				
				if(!id.toString().equalsIgnoreCase(input)) {
					System.out.println("UUID inválido. Digite o ID completo. ");
					continue;
				}
				
				return id;
			}
			catch(IllegalArgumentException e)
			{
				System.out.println("ID inválido. Tente novamente.");
			}
		}
	}
	
	private static UUID readProductId(Scanner sc) {
		while(true) {
			UUID id = readUUID(sc, "Digite o ID do produto: ");
			ReadOnlyProduct prod = productService.findById(id);
			
			if(prod == null) {
				System.out.println("Produto não encontrado, tente novamente..");
				continue;
			}
			
			return id;
		}
	}
	
	private static UUID readCategoryId(Scanner sc) {
		if(categoryService.getCategories().isEmpty())
			throw new IllegalStateException("Sem categorias cadastradas no momento.");
		
		while(true) {
			UUID id = readUUID(sc, "Digite o ID da categoria: ");

			ReadOnlyCategory category = categoryService.getCategories()
					.stream()
					.filter(c -> c.getId().equals(id))
					.findFirst()
					.orElse(null);
			
			if(category == null) {
				System.out.println("Categoria não encontrada. Tente novamente.");
				continue;
			}
			
			if(!category.isActive()) {
				System.out.println("Categoria inativa. Escolha uma categoria ativa. ");
				continue;
			}
			
			return id;
		}
	}
	
	private static LocalDate readDate(Scanner sc, String message, DateTimeFormatter formatter) {
		while (true) {
			System.out.println(message);
			String input = sc.nextLine().trim();
			try 
			{
				return LocalDate.parse(input, formatter);
			} catch (DateTimeParseException d) {
				System.out.println("Date inválida. Utilize o formato dd/MM/uuuu.");
			}
		}
	}
	
	private static BigDecimal readPositiveQuantity(Scanner sc) {
		while(true) {
			System.out.println("Quantidade recebida: ");
			String input = sc.nextLine().trim().replace(',', '.');
			try {
				BigDecimal quantity = new BigDecimal(input);
				if(quantity.compareTo(BigDecimal.ZERO) <= 0) {
					System.out.println("A quantidade deve ser maior que zero.");
					continue;
				}
				
				return quantity;
			}
			catch(NumberFormatException number) {
				System.out.println("Quantidade inválida. Digite um número válido.");
			}
		}
	}
	
	private static LocalDate readEntryDate(Scanner sc, String message, DateTimeFormatter formatter) {
		LocalDate today = LocalDate.now();
		
		while(true) {
			LocalDate date = readDate(sc, message, formatter);
			
			if(!date.equals(today)) {
				System.out.println("A data de entrada deve ser a data de hoje.");
				continue;
			}
			return date;	
		}
	}
	
	private static LocalDate readExpiresDate(Scanner sc, String string, DateTimeFormatter formatter, LocalDate entry) {
		while(true) {
			LocalDate dateExpires = readDate(sc, string, formatter);
			
			if(dateExpires.isBefore(entry)) {
				System.out.println("A data de validade deve ser igual ou posterior à entrada.");
				continue;
			}
			
			return dateExpires;
		}
	}
}
