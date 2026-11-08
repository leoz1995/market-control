package marketcontrol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

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
		try {
			Scanner sc = new Scanner(System.in);
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
					System.out.println(searchBarCode(barCode));
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
				default:
					System.out.println("Opção inválida!");

				}
			} while (!option.equals("8"));
			sc.close();
		} 
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	private static void addProduct(Scanner sc) {
		System.out.println("Digite o nome do produto: ");
		String name = sc.nextLine();

		System.out.println("Digite o código de barras do produto: ");
		String code = sc.nextLine();

		System.out.println("Digite a descrição do produto: ");
		String desc = sc.nextLine();

		System.out.println(categoryService.getCategories());
		System.out.println("Digite o ID da categoria do produto: ");
		String categoryId = sc.nextLine();
		UUID catId = null;
		catId = UUID.fromString(categoryId);
		
		ReadOnlyProduct save = productService.saveProduct(name, code, desc, true, catId);

		System.out.println("Produtos cadastrados: ");
		System.out.println(save.getName());
	}

	private static void productList() {
		List<ReadOnlyProduct> saved = productService.getProducts();
		saved.forEach(p -> System.out.println(p));
	}
	
	private static void lotList() {
		List<ReadOnlyLot> saved = lotService.getLots();
		saved.forEach(p -> System.out.println(p));
	}
	
	private static ProductDTO searchBarCode(String barCode) {
		ProductDTO products = productService.searchBarCode(barCode);
		return products;
	}

	public static void productEdit(Scanner sc) {
		productList();
		System.out.println("Digite o codigo de barras do produto à ser editado: ");
		String productId = sc.nextLine();
		
		ReadOnlyProduct p = productService.searchProduct(productId);
		//IdDoProduto
		
		System.out.println("Digite o nome do produto: ");
		String n = sc.nextLine();
		
		System.out.println("Digite o código de barras: ");
		String bc = sc.nextLine();

		System.out.println("Digite a descrição: ");
		String desc = sc.nextLine();
		
		System.out.println("O produto ficará ativo? S/N");
		String active = sc.nextLine().trim();
		
		boolean isActive = active.toUpperCase().startsWith("S") ? true : false;
		
		categoryService.getCategories();
		System.out.println("Qual o ID da categoria?");
		UUID id = null;
		String categoryId = sc.nextLine();
		
		id = UUID.fromString(categoryId);
		
		ProductDTO updated = new ProductDTO(n, bc, desc, isActive, id);
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
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		UUID id = null;
		productList();
		System.out.println("Digite o ID do produto: ");
		String identification = sc.nextLine();
		id = UUID.fromString(identification);

		System.out.println("Digite a DATA de entrada (dd/MM/yyyy): ");
		String entrada = sc.nextLine().trim();
		LocalDate dataEntry = LocalDate.parse(entrada, formatter);

		System.out.println("Digite a DATA de validade (dd/MM/yyyy): ");
		String entrada2 = sc.nextLine().trim();
		LocalDate dataExpires = LocalDate.parse(entrada2, formatter);

		System.out.println("Quantidade recebida: ");
		String quantity = sc.nextLine();
		BigDecimal total = new BigDecimal(quantity);

		ReadOnlyLot save = lotService.save(id, dataEntry, dataExpires, total);

		System.out.println("Novo LOTE inserido: ");
		System.out.println(save.getId() + " | " + save.getEntryDate() + " | " + save.getExpirationDate() + " | "
				+ save.getInitialQuantity());
	}
	
	private static void consultLotAndStock(Scanner sc) {
		UUID uuid = null;
		UUID lotUuid = null;
		System.out.println("Digite o ID do PRODUTO que deseja ver o STOCK: ");
		String id = sc.nextLine();
		uuid = UUID.fromString(id);
		
		System.out.println(lotService.getTotalAvailableQuantity(uuid));
		
		System.out.println("---------------------------------");
		lotList();
		System.out.println("Insira o ID do lote: ");
		
		String lotId = sc.nextLine();
		lotUuid = UUID.fromString(lotId);
		
		System.out.println(lotService.findById(lotUuid));
	}
}
