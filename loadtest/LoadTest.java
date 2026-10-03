import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Load test for CommerceHub. Talks to the RUNNING back end over HTTP, like real customers.
 *
 * Run from the commercehub folder (back end must be running on port 8090, in demo payment mode):
 *     java loadtest\LoadTest.java
 *     java loadtest\LoadTest.java 300        (300 customers instead of 200)
 *
 * Scenario A - "flash sale":    every customer checks out 1 unit of a product with only 20 in stock,
 *                               all at the same instant. Exactly 20 orders may succeed.
 * Scenario B - "double cancel": each of those 20 orders is cancelled by two requests at the same
 *                               instant. The stock must come back once, not twice.
 * Scenario C - "mixed rush":    every customer checks out 1 to 3 units of a product with 50 in stock.
 *                               Units sold must equal units ordered, and stock must not go below zero.
 */
public class LoadTest {

    static final String BASE = "http://localhost:8090";
    static final String PASSWORD = "LoadTest#123";
    static final int FLASH_STOCK = 20;
    static final int RUSH_STOCK = 50;

    static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static final AtomicInteger RETRIES = new AtomicInteger();

    record Result(int status, long millis, String body, int index) { }

    public static void main(String[] args) throws Exception {

        int customers = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        String runId = Long.toString(System.currentTimeMillis(), 36).toUpperCase();

        System.out.println("CommerceHub load test - " + customers + " customers, run " + runId);

        // ---------- set-up: admin creates two products with limited stock ----------
        HttpResponse<String> adminLogin = send("POST", "/api/auth/login", null,
                "{\"email\":\"admin@commercehub.local\",\"password\":\"admin123\"}");
        String admin = find(adminLogin.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");

        if (admin == null) {
            System.out.println("Could not log in as admin. Is the back end running on port 8090?");
            System.out.println("Response: " + adminLogin.statusCode() + " " + adminLogin.body());
            return;
        }

        String categoryId = find(send("GET", "/api/categories", null, null).body(), "\"id\"\\s*:\\s*(\\d+)");
        String flashId = createProduct(admin, "LT-" + runId + "-A", "Flash Sale Item " + runId, FLASH_STOCK, categoryId);
        String rushId = createProduct(admin, "LT-" + runId + "-B", "Mixed Rush Item " + runId, RUSH_STOCK, categoryId);

        if (flashId == null || rushId == null) {
            System.out.println("Could not create the test products.");
            return;
        }

        // ---------- set-up: register customers, each with 1 flash-sale item in the cart ----------
        System.out.println("Registering " + customers + " customers (takes a little while)...");

        String[] tokens = new String[customers];
        ExecutorService setupPool = Executors.newFixedThreadPool(8);
        List<Future<?>> setup = new ArrayList<>();

        for (int i = 0; i < customers; i++) {
            int index = i;
            setup.add(setupPool.submit(() -> {
                String email = "loadtest-" + runId.toLowerCase() + "-" + index + "@example.com";
                HttpResponse<String> registered = send("POST", "/api/auth/register", null,
                        "{\"name\":\"Load Test\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
                String token = find(registered.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");
                tokens[index] = token;
                if (token != null) {
                    send("POST", "/api/cart/items", token, "{\"productId\":" + flashId + ",\"quantity\":1}");
                }
                return null;
            }));
        }
        for (Future<?> f : setup) {
            f.get();
        }

        for (String token : tokens) {
            if (token == null) {
                System.out.println("Some customers could not be registered. Stopping.");
                setupPool.shutdown();
                return;
            }
        }

        for (int i = 0; i < 20; i++) {
            send("GET", "/api/products/" + flashId, null, null);   // warm-up
        }

        String checkoutBody = "{\"shippingAddress\":\"Load test address\"}";

        // ---------- Scenario A: flash sale ----------
        System.out.println("Scenario A: " + customers + " customers check out the same " + FLASH_STOCK + "-unit product at once...");

        List<Result> flash = fire(customers, i -> () -> send("POST", "/api/checkout", tokens[i], checkoutBody));

        long flashWon = flash.stream().filter(r -> r.status() == 201).count();
        long flashSoldOut = flash.stream().filter(r -> r.status() == 409).count();
        long flashOther = flash.size() - flashWon - flashSoldOut;
        int flashStockAfter = stockOf(flashId);

        // ---------- Scenario B: every winning order cancelled twice at the same instant ----------
        List<String[]> winners = new ArrayList<>();   // {token, orderNumber}
        for (Result r : flash) {
            if (r.status() == 201) {
                winners.add(new String[] {tokens[r.index()], find(r.body(), "\"orderNumber\"\\s*:\\s*\"([^\"]+)\"")});
            }
        }

        System.out.println("Scenario B: " + winners.size() + " orders each cancelled by two requests at once...");

        List<Result> cancels = fire(winners.size() * 2, i -> () -> {
            String[] winner = winners.get(i / 2);
            return send("POST", "/api/orders/" + winner[1] + "/cancel", winner[0], null);
        });

        long cancelOk = cancels.stream().filter(r -> r.status() == 200).count();
        long cancelRefused = cancels.stream().filter(r -> r.status() == 400).count();
        long cancelOther = cancels.size() - cancelOk - cancelRefused;
        int flashStockRestored = stockOf(flashId);

        // ---------- Scenario C: mixed quantities ----------
        Random random = new Random(7);
        int[] wanted = new int[customers];
        List<Future<?>> refill = new ArrayList<>();

        for (int i = 0; i < customers; i++) {
            int index = i;
            wanted[i] = 1 + random.nextInt(3);
            refill.add(setupPool.submit(() -> {
                // Customers who lost the flash sale still have that item in the cart: take it out
                send("DELETE", "/api/cart/items/" + flashId, tokens[index], null);
                send("POST", "/api/cart/items", tokens[index],
                        "{\"productId\":" + rushId + ",\"quantity\":" + wanted[index] + "}");
                return null;
            }));
        }
        for (Future<?> f : refill) {
            f.get();
        }
        setupPool.shutdown();

        System.out.println("Scenario C: " + customers + " customers check out 1 to 3 units of a " + RUSH_STOCK + "-unit product at once...");

        List<Result> rush = fire(customers, i -> () -> send("POST", "/api/checkout", tokens[i], checkoutBody));

        long rushWon = rush.stream().filter(r -> r.status() == 201).count();
        long rushSoldOut = rush.stream().filter(r -> r.status() == 409).count();
        long rushOther = rush.size() - rushWon - rushSoldOut;
        int unitsOrdered = rush.stream().filter(r -> r.status() == 201).mapToInt(r -> wanted[r.index()]).sum();
        int rushStockAfter = stockOf(rushId);
        int unitsGone = RUSH_STOCK - rushStockAfter;

        // ---------- results ----------
        List<Long> times = new ArrayList<>();
        flash.forEach(r -> times.add(r.millis()));
        rush.forEach(r -> times.add(r.millis()));
        Collections.sort(times);

        boolean checkFlash = flashWon == FLASH_STOCK && flashStockAfter == 0;
        boolean checkCancel = cancelOk == winners.size() && flashStockRestored == FLASH_STOCK;
        boolean checkRush = unitsOrdered == unitsGone && rushStockAfter >= 0;
        // Stock only ever goes down, so anyone refused must have wanted more than what is left at the end
        boolean checkNoWaste = rush.stream()
                .filter(r -> r.status() == 409)
                .allMatch(r -> wanted[r.index()] > rushStockAfter);
        boolean checkErrors = flashOther == 0 && cancelOther == 0 && rushOther == 0;

        StringBuilder out = new StringBuilder();
        out.append("# CommerceHub load test report\n\n");
        out.append("Run: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append(" | customers: ").append(customers)
                .append(" | checkout requests: ").append(times.size()).append("\n\n");

        out.append("## Scenario A - flash sale (").append(FLASH_STOCK).append(" units, 1 each)\n");
        out.append("- orders placed: ").append(flashWon).append("\n");
        out.append("- told \"sold out\": ").append(flashSoldOut).append("\n");
        out.append("- stock left: ").append(flashStockAfter).append("\n");
        out.append("- unexpected responses: ").append(flashOther).append(statuses(flash, 201, 409)).append("\n\n");

        out.append("## Scenario B - each order cancelled twice at the same instant\n");
        out.append("- orders cancelled: ").append(cancelOk).append(" of ").append(winners.size()).append("\n");
        out.append("- duplicate cancels refused: ").append(cancelRefused).append("\n");
        out.append("- stock after cancels: ").append(flashStockRestored).append(" (started at ").append(FLASH_STOCK).append(")\n");
        out.append("- unexpected responses: ").append(cancelOther).append(statuses(cancels, 200, 400)).append("\n\n");

        out.append("## Scenario C - mixed rush (").append(RUSH_STOCK).append(" units, 1 to 3 each)\n");
        out.append("- orders placed: ").append(rushWon).append("\n");
        out.append("- told \"sold out\": ").append(rushSoldOut).append("\n");
        out.append("- units in placed orders: ").append(unitsOrdered).append("\n");
        out.append("- units gone from stock: ").append(unitsGone).append("\n");
        out.append("- stock left: ").append(rushStockAfter).append("\n");
        out.append("- unexpected responses: ").append(rushOther).append(statuses(rush, 201, 409)).append("\n\n");

        out.append("## Response time (checkout requests, sent at once)\n");
        out.append("- p50: ").append(percentile(times, 50)).append(" ms\n");
        out.append("- p95: ").append(percentile(times, 95)).append(" ms\n");
        out.append("- max: ").append(times.get(times.size() - 1)).append(" ms\n\n");

        out.append("## Connections\n");
        out.append("- connection attempts retried: ").append(RETRIES.get()).append("\n\n");

        out.append("## Checks\n");
        out.append(line(checkFlash, "flash sale: exactly " + FLASH_STOCK + " orders, stock ends at 0"));
        out.append(line(checkCancel, "double cancel: every order cancelled once, stock back to exactly " + FLASH_STOCK));
        out.append(line(checkRush, "mixed rush: units sold equal units in orders, stock never below 0"));
        out.append(line(checkNoWaste, "mixed rush: nobody was turned away while enough stock was left"));
        out.append(line(checkErrors, "no unexpected errors"));

        System.out.println();
        System.out.println(out);

        Files.createDirectories(Path.of("loadtest"));
        Files.writeString(Path.of("loadtest", "latest-report.md"), out.toString());
        System.out.println("Saved to loadtest\\latest-report.md");
    }

    static String createProduct(String admin, String sku, String name, int stock, String categoryId) throws Exception {

        HttpResponse<String> created = send("POST", "/api/admin/products", admin,
                "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"description\":\"Created by the load test\","
                        + "\"price\":100,\"stock\":" + stock + ",\"categoryId\":" + categoryId + ",\"active\":true}");

        String id = find(created.body(), "\"id\"\\s*:\\s*(\\d+)");
        if (id == null) {
            System.out.println("Product response: " + created.statusCode() + " " + created.body());
        }
        return id;
    }

    static int stockOf(String productId) throws Exception {
        String stock = find(send("GET", "/api/products/" + productId, null, null).body(), "\"stock\"\\s*:\\s*(-?\\d+)");
        return stock == null ? Integer.MIN_VALUE : Integer.parseInt(stock);
    }

    interface TaskFactory {
        Callable<HttpResponse<String>> forIndex(int index);
    }

    /** Runs n requests, all released at the same instant. */
    static List<Result> fire(int n, TaskFactory factory) throws Exception {

        if (n == 0) {
            return new ArrayList<>();
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {

            int index = i;
            Callable<HttpResponse<String>> call = factory.forIndex(i);

            futures.add(pool.submit(() -> {

                ready.countDown();
                start.await();

                long begin = System.nanoTime();
                int status = -1;
                String body = "";

                try {
                    HttpResponse<String> response = call.call();
                    status = response.statusCode();
                    body = response.body();
                } catch (Exception ex) {
                    body = ex.toString();
                }

                return new Result(status, (System.nanoTime() - begin) / 1_000_000, body, index);
            }));
        }

        ready.await(30, TimeUnit.SECONDS);
        start.countDown();

        List<Result> results = new ArrayList<>();
        for (Future<Result> f : futures) {
            results.add(f.get());
        }

        pool.shutdown();
        return results;
    }

    /** One HTTP call. A refused connection is retried, as a real client would, and counted. */
    static HttpResponse<String> send(String method, String path, String token, String json) throws Exception {

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(120));

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        if (json != null) {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }

        Exception last = null;

        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (java.net.ConnectException ex) {
                last = ex;
                RETRIES.incrementAndGet();
                Thread.sleep(200L * attempt);
            }
        }

        throw last;
    }

    static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : null;
    }

    static long percentile(List<Long> sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    static String statuses(List<Result> results, int okA, int okB) {

        Map<Integer, Integer> counts = new TreeMap<>();
        String sample = "";

        for (Result r : results) {
            if (r.status() != okA && r.status() != okB) {
                counts.merge(r.status(), 1, Integer::sum);
                if (sample.isEmpty()) {
                    sample = r.body().length() > 160 ? r.body().substring(0, 160) : r.body();
                }
            }
        }

        return counts.isEmpty() ? "" : "  (status codes: " + counts + ", first: " + sample + ")";
    }

    static String line(boolean ok, String text) {
        return "- " + (ok ? "PASS" : "FAIL") + ": " + text + "\n";
    }
}
