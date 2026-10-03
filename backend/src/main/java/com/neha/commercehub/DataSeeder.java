package com.neha.commercehub;

import com.neha.commercehub.catalog.Category;
import com.neha.commercehub.catalog.CategoryRepository;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.catalog.ProductRepository;
import com.neha.commercehub.user.Role;
import com.neha.commercehub.user.User;
import com.neha.commercehub.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Fills an empty database with sample products and two demo accounts, so the shop
 * has something to show on first start. Does nothing if data already exists.
 * For local development only; switch off with app.seed.enabled=false.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(CategoryRepository categoryRepository,
                      ProductRepository productRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.enabled}") boolean enabled,
                      @Value("${app.seed.admin-email}") String adminEmail,
                      @Value("${app.seed.admin-password}") String adminPassword) {

        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {

        if (!enabled) {
            return;
        }

        if (!userRepository.existsByEmail(adminEmail)) {
            userRepository.save(new User("Shop Admin", adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN));
            userRepository.save(new User("Demo Customer", "customer@commercehub.local",
                    passwordEncoder.encode("customer123"), Role.CUSTOMER));
            log.info("Created demo accounts: {} (admin) and customer@commercehub.local", adminEmail);
        }

        if (productRepository.count() > 0) {
            return;
        }

        Category electronics = categoryRepository.save(new Category("Electronics"));
        Category kitchen = categoryRepository.save(new Category("Home & Kitchen"));
        Category books = categoryRepository.save(new Category("Books"));
        Category fitness = categoryRepository.save(new Category("Fitness"));
        Category stationery = categoryRepository.save(new Category("Stationery"));

        add("ELEC-001", "Wireless Earbuds", "Bluetooth earbuds with charging case and 24-hour battery.", "1899", 40, electronics);
        add("ELEC-002", "Mechanical Keyboard", "Compact 75% keyboard with tactile switches and white backlight.", "3499", 18, electronics);
        add("ELEC-003", "USB-C Fast Charger 65W", "Charges a laptop and a phone at the same time.", "1499", 60, electronics);
        add("ELEC-004", "Portable SSD 1TB", "Pocket-size drive for backups and large files.", "6999", 9, electronics);
        add("ELEC-005", "Smart LED Bulb", "Dimmable, 16 million colours, works with voice assistants.", "599", 120, electronics);
        add("ELEC-006", "Noise-Cancelling Headphones", "Over-ear headphones for travel and focus.", "8999", 4, electronics);

        add("HOME-001", "Stainless Steel Water Bottle 1L", "Keeps drinks cold for 24 hours.", "749", 85, kitchen);
        add("HOME-002", "Cast Iron Tawa", "Pre-seasoned, 26 cm, for rotis and dosas.", "1299", 22, kitchen);
        add("HOME-003", "Electric Kettle 1.5L", "Boils in three minutes, auto shut-off.", "1099", 35, kitchen);
        add("HOME-004", "Glass Storage Jars (Set of 6)", "Airtight jars for the pantry.", "899", 3, kitchen);
        add("HOME-005", "Cotton Bedsheet, Double", "Soft 180-thread-count cotton with two pillow covers.", "1599", 27, kitchen);

        add("BOOK-001", "Designing Data-Intensive Applications", "How large data systems really work.", "1450", 14, books);
        add("BOOK-002", "Clean Code", "A handbook of agile software craftsmanship.", "620", 30, books);
        add("BOOK-003", "The Pragmatic Programmer", "Practical advice for working developers.", "780", 2, books);
        add("BOOK-004", "Atomic Habits", "Small changes, remarkable results.", "399", 75, books);
        add("BOOK-005", "The Psychology of Money", "Timeless lessons on wealth and happiness.", "349", 50, books);

        add("FIT-001", "Yoga Mat 6mm", "Non-slip mat with carry strap.", "899", 45, fitness);
        add("FIT-002", "Adjustable Dumbbells (Pair)", "2.5 kg to 12 kg per dumbbell.", "4999", 6, fitness);
        add("FIT-003", "Resistance Bands (Set of 5)", "Five strengths for home workouts.", "549", 90, fitness);
        add("FIT-004", "Skipping Rope", "Ball-bearing handles, adjustable length.", "299", 5, fitness);

        add("STAT-001", "Dot Grid Notebook A5", "160 pages, lay-flat binding.", "349", 110, stationery);
        add("STAT-002", "Gel Pens (Pack of 10)", "Smooth 0.5 mm black ink.", "199", 200, stationery);
        add("STAT-003", "Desk Organiser", "Bamboo organiser with five compartments.", "799", 16, stationery);
        add("STAT-004", "Sticky Notes (Pack of 12)", "Assorted colours, 100 sheets each.", "249", 1, stationery);

        log.info("Created {} sample products.", productRepository.count());
    }

    private void add(String sku, String name, String description, String price, int stock, Category category) {
        productRepository.save(new Product(sku, name, description, new BigDecimal(price), stock, category));
    }
}
