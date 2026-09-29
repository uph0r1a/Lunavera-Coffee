// Dev-only check (not part of the Maven build, lives outside src/). Runs the real SessionGuard +
// UserDAO against a throwaway SQLite database. See progress.md ("...merged into the audit
// branch") for how to compile/run without Maven: javac against the JavaFX + sqlite-jdbc + slf4j
// jars, -Duser.home pointed at an empty temp dir so it never touches ~/.lunavera-coffee.
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

public class SessionGuardCheck {
    static int failures = 0;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }
    public static void main(String[] a) {
        DatabaseConfig.initialize();
        UserDAO dao = new UserDAO();

        check("findById(missing) is empty", dao.findById(987654).isEmpty());
        User admin = dao.findByUsername("admin").orElseThrow();
        check("findById(admin) finds seeded admin", dao.findById(admin.getId()).isPresent());

        Session.clear();
        check("no session -> invalid", !SessionGuard.validateNow());

        Session.start(admin, null);
        check("active admin -> valid", SessionGuard.validateNow());

        User emp = new User();
        emp.setUsername("guardtest");
        emp.setPasswordHash(PasswordUtil.hash("Test1234"));
        emp.setRole(Role.EMPLOYEE);
        emp.setStatus(AccountStatus.ACTIVE);
        emp = dao.insert(emp);

        Session.start(emp, null);
        check("active employee -> valid", SessionGuard.validateNow());
        check("employee is not admin", !Session.isAdmin());

        dao.updateRole(emp.getId(), Role.ADMIN);
        check("still valid after promotion", SessionGuard.validateNow());
        check("session role refreshed to ADMIN without re-login", Session.isAdmin());

        dao.updateRole(emp.getId(), Role.EMPLOYEE);
        SessionGuard.validateNow();
        check("session role refreshed back to EMPLOYEE (demotion)", !Session.isAdmin());

        dao.updateStatus(emp.getId(), AccountStatus.LOCKED);
        check("LOCKED account -> invalid", !SessionGuard.validateNow());

        dao.updateStatus(emp.getId(), AccountStatus.ACTIVE);
        check("re-activated account -> valid again", SessionGuard.validateNow());

        User ghost = new User();
        ghost.setId(424242);
        ghost.setRole(Role.EMPLOYEE);
        ghost.setStatus(AccountStatus.ACTIVE);
        Session.start(ghost, null);
        check("account that no longer exists -> invalid", !SessionGuard.validateNow());

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures);
    }
}
