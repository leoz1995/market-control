CREATE TABLE lots (
	id CHAR(36) PRIMARY KEY,
	product_id CHAR(36) NOT NULL,
	FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
	entry_date DATE NOT NULL,
	expiration_date DATE NOT NULL,
	initial_quantity DECIMAL(15,3) NOT NULL,
	available_quantity DECIMAL(15,3) NOT NULL
);