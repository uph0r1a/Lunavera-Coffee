// Dev-only check (not part of the Maven build, lives outside src/). Verifies the starter-menu
// seed (MenuSeeder) through DatabaseConfig's real public entry point, and - the actual point of
// this check - that deleting a seeded item survives a genuine fresh-process restart instead of
// coming back. That needs two separate `java` invocations against the same -Duser.home (a
// single JVM can't exercise this: DatabaseConfig's "already initialized" flag would just skip
// the second call, masking the persistent-flag logic in MenuSeeder that this is actually about).
// Compile/run the same way as SessionGuardCheck.java (see progress.md); run twice:
//   java -Duser.home=$TMP -cp ... MenuSeederCheck seed
//   java -Duser.home=$TMP -cp ... MenuSeederCheck verify-deletion-stuck
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MenuSeederCheck {
    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        DatabaseConfig.initialize();
        ProductDAO productDAO = new ProductDAO();
        CategoryDAO categoryDAO = new CategoryDAO();
        String mode = args.length > 0 ? args[0] : "seed";

        if (mode.equals("seed")) {
            List<Product> products = productDAO.findAll();
            check("exactly 33 starter products seeded", products.size() == 33);

            Set<String> categoryNames = categoryDAO.findAll().stream().map(Category::getName).collect(Collectors.toSet());
            check("all 4 starter categories present", categoryNames.containsAll(
                    Set.of("Cà phê", "Bánh ngọt", "Nước ép & Sinh tố", "Trà")));

            check("every seeded product has an image path", products.stream().allMatch(p -> p.getImagePath() != null));
            check("every seeded product has a positive price", products.stream().allMatch(p -> p.getPrice().signum() > 0));

            Product target = products.stream().filter(p -> p.getName().equals("Cafe đen")).findFirst().orElse(null);
            check("'Cafe đen' is one of the seeded products", target != null);
            if (target != null) {
                productDAO.delete(target.getId());
                check("'Cafe đen' deleted successfully", productDAO.findAll().stream()
                        .noneMatch(p -> p.getId() == target.getId()));
            }
        } else if (mode.equals("verify-deletion-stuck")) {
            // This is the real point of the check: a fresh process (DatabaseConfig.initialized
            // starts false again) re-running initialize() must NOT bring "Cafe đen" back.
            List<Product> products = productDAO.findAll();
            check("count stayed at 32 after a restart (not back to 33)", products.size() == 32);
            check("'Cafe đen' is still gone after a fresh restart",
                    products.stream().noneMatch(p -> p.getName().equals("Cafe đen")));
        } else {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)");
        if (failures > 0) System.exit(1);
    }
}
