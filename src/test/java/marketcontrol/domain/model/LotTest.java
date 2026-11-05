package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

public class LotTest {
	final Category cat = Category.create("Grãos", "Melhores grãos", true);
	final Product p = Product.create("Feijão", "12345678900A", "Enlatados", true, cat);
	
	@Test
	void shouldCreateLotWithValidData() {
	    BigDecimal initialQuantity = new BigDecimal("10000");

	    final LocalDate entryDate = LocalDate.now();
	    final LocalDate expirationDate = LocalDate.of(2026, Month.DECEMBER, 17);

	    Lot lot = Lot.create(
	        p,
	        entryDate,
	        expirationDate,
	        initialQuantity
	    );

	    assertNotNull(lot);
	    assertEquals(
	    	    0,
	    	    initialQuantity.compareTo(lot.getAvailableQuantity())
	    	);
	}

	@Test
	void shouldNotCreateLotWithoutProduct() {
	    BigDecimal initialQuantity = new BigDecimal("10000");

	    final LocalDate entryDate = LocalDate.now();
	    final LocalDate expirationDate = LocalDate.of(2026, Month.DECEMBER, 17);

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            null,
	            entryDate,
	            expirationDate,
	            initialQuantity
	        )
	    );
	}
	
	@Test
	void shouldNotCreateLotWithoutEntryDate() {
	    BigDecimal initialQuantity = new BigDecimal("10000");

	    final LocalDate expirationDate = LocalDate.of(2026, Month.DECEMBER, 17);

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            p,
	            null,
	            expirationDate,
	            initialQuantity
	        )
	    );
	}

	@Test
	void shouldNotCreateLotWithoutExpirationDate() {
	    BigDecimal initialQuantity = new BigDecimal("10000");

	    final LocalDate entryDate = LocalDate.now();

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            p,
	            entryDate,
	            null,
	            initialQuantity
	        )
	    );
	}
	
	@Test
	void shouldNotCreateLotWithNullInitialQuantity() {
	    final LocalDate entryDate = LocalDate.now();
	    final LocalDate expirationDate = LocalDate.of(2026, Month.DECEMBER, 17);

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            p,
	            entryDate,
	            expirationDate,
	            null
	        )
	    );
	}

	@Test
	void shouldNotCreateLotWithoutInitialQuantityZero() {
	    BigDecimal initialQuantity = new BigDecimal("0");

	    final LocalDate entryDate = LocalDate.now();
	    final LocalDate expirationDate = LocalDate.of(2026, Month.DECEMBER, 17);

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            p,
	            entryDate,
	            expirationDate,
	            initialQuantity
	        )
	    );
	    
	}

	@Test
	void shouldNotCreateLotWithoutInitialQuantityNegative() {
	    BigDecimal initialQuantity = new BigDecimal("-10");

	    final LocalDate entryDate = LocalDate.now();
	    final LocalDate expirationDate = LocalDate.of(2026, Month.DECEMBER, 17);

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            p,
	            entryDate,
	            expirationDate,
	            initialQuantity
	        )
	    );
	}
	
	@Test
	void shouldNotCreateLotWhenExpirationDateIsBeforeEntryDate() {
	    BigDecimal initialQuantity = new BigDecimal("1000");

	    final LocalDate entryDate = LocalDate.now();
	    final LocalDate expirationDate = LocalDate.of(2025, Month.DECEMBER, 17);

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> Lot.create(
	            p,
	            entryDate,
	            expirationDate,
	            initialQuantity
	        )
	    );
	    
	}
	
	@Test
	void shouldConsumeAvailableQuantity() {
	    BigDecimal initialQuantity = new BigDecimal("100");

	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );

	    lot.consume(new BigDecimal("30"));

	    assertEquals(
	        0,
	        lot.getAvailableQuantity().compareTo(new BigDecimal("70"))
	    );
	}
	
	@Test
	void shouldInitializeAvailableQuantityWithInitialQuantity() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");

	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );

	    assertEquals(
	        0,
	        initialQuantity.compareTo(lot.getAvailableQuantity())
	    );
	}
	
	@Test
	void shouldConsumeFractionalQuantity() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    BigDecimal consume = new BigDecimal("85.3");

	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );
	    lot.consume(consume);
	    
	    assertEquals(
	        initialQuantity.subtract(consume),
	        lot.getAvailableQuantity()
	    );
	}

	@Test
	void shouldConsumeAllAvailableQuantity() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    BigDecimal consume = new BigDecimal("100.0");

	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );
	    lot.consume(consume);
	    
	    assertEquals(
	        initialQuantity.subtract(consume),
	        lot.getAvailableQuantity()
	    );
	}

	@Test
	void shouldRejectConsumptionGreaterThanAvailableQuantity() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    BigDecimal consume = new BigDecimal("110.0");
	    
	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );
	    
	    assertThrows(
		        IllegalArgumentException.class,
		        () -> lot.consume(
		            consume
		        )
		    );
	    
	    assertEquals(
		        initialQuantity,
		        lot.getAvailableQuantity()
		    );
	}
	
	@Test
	void shouldRejectNullConsumption() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    
	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );
	    assertThrows(
		        IllegalArgumentException.class,
		        () -> lot.consume(
		            null
		        )
		    );
	}

	@Test
	void shouldRejectZeroConsumption() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    
	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );
	    assertThrows(
		        IllegalArgumentException.class,
		        () -> lot.consume(
		            BigDecimal.ZERO
		        )
		    );
	}

	@Test
	void shouldRejectNegativeConsumption() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    
	    Lot lot = Lot.create(
	        p,
	        LocalDate.now(),
	        LocalDate.now().plusDays(30),
	        initialQuantity
	    );
	    assertThrows(
		        IllegalArgumentException.class,
		        () -> lot.consume(
		        		new BigDecimal("-10.00")
		        )
		    );
	}
	
	@Test
	void shouldVerifyExpiredLot() {
	    BigDecimal initialQuantity = new BigDecimal("100.00");
	    
	    Lot lot = Lot.create(
	        p,
	        LocalDate.of(2026, 10, 1),
	        LocalDate.of(2026, 10, 7),
	        initialQuantity
	    );
	    
	    assertThrows(
		        IllegalArgumentException.class,
		        () -> lot.consume(
		        		new BigDecimal("10.00")
		        )
		    );
	    
	    assertTrue(
		        lot.isExpired()
		    );
	    
	    assertEquals(0, lot.getAvailableQuantity().compareTo(initialQuantity));
	    
	}
}
