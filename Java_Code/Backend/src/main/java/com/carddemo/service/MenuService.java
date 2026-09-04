package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.dto.OperationsDtos.MenuOption;
import com.carddemo.dto.OperationsDtos.MenuView;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Menu dispatch. COBOL sources {@code COMEN01C.cbl} with option table {@code COMEN02Y.cpy}
 * (eleven regular entries) and {@code COADM01C.cbl} with option table {@code COADM02Y.cpy}
 * (six administrator entries).
 *
 * <p>The option numbers, texts and target program names below are exactly the copybook values.
 * Each entry also carries the Angular route that replaced the CICS {@code XCTL}.</p>
 *
 * <p>Admin options 5-6 (transaction type Db2 module) belong to an optional module. The legacy
 * programs reported "not installed" for an absent one; the {@code installed} flag reproduces that
 * navigation result instead of failing (FR-OPT-017).</p>
 *
 * <p>Option 11 was reported the same way until the authorization module was converted. It is now
 * installed and reaches the pending authorization screens ({@code COPAUS0C} / {@code COPAUS1C}).</p>
 */
@Service
public class MenuService {

    private static final String TITLE = "CardDemo";

    /** {@code COMEN02Y.cpy} lines 19-98: the eleven main menu entries, all tagged user type U. */
    private static final List<MenuOption> MAIN_MENU = List.of(
            new MenuOption(1, "Account View", "COACTVWC", "U", "/accounts/view", true),
            new MenuOption(2, "Account Update", "COACTUPC", "U", "/accounts/update", true),
            new MenuOption(3, "Credit Card List", "COCRDLIC", "U", "/cards", true),
            new MenuOption(4, "Credit Card View", "COCRDSLC", "U", "/cards/view", true),
            new MenuOption(5, "Credit Card Update", "COCRDUPC", "U", "/cards/update", true),
            new MenuOption(6, "Transaction List", "COTRN00C", "U", "/transactions", true),
            new MenuOption(7, "Transaction View", "COTRN01C", "U", "/transactions/view", true),
            new MenuOption(8, "Transaction Add", "COTRN02C", "U", "/transactions/add", true),
            new MenuOption(9, "Transaction Reports", "CORPT00C", "U", "/reports", true),
            new MenuOption(10, "Bill Payment", "COBIL00C", "U", "/bill-payment", true),
            new MenuOption(11, "Pending Authorization View", "COPAUS0C", "U", "/pending-authorizations", true));

    /** {@code COADM02Y.cpy} lines 19-59: the six administrator menu entries. */
    private static final List<MenuOption> ADMIN_MENU = List.of(
            new MenuOption(1, "User List (Security)", "COUSR00C", "A", "/admin/users", true),
            new MenuOption(2, "User Add (Security)", "COUSR01C", "A", "/admin/users/add", true),
            new MenuOption(3, "User Update (Security)", "COUSR02C", "A", "/admin/users/update", true),
            new MenuOption(4, "User Delete (Security)", "COUSR03C", "A", "/admin/users/delete", true),
            new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC", "A", "/admin/transaction-types", true),
            new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", "A",
                    "/admin/transaction-types/maintain", true));

    /** The main menu, transaction {@code CM00}. */
    public MenuView mainMenu(String role) {
        return new MenuView(TITLE, "CM00", "COMEN01C", role, MAIN_MENU);
    }

    /** The administrator menu, transaction {@code CA00}. */
    public MenuView adminMenu(String role) {
        return new MenuView(TITLE, "CA00", "COADM01C", role, ADMIN_MENU);
    }

    /**
     * Option selection, reproducing the COBOL edit: the two-character input is right trimmed,
     * remaining spaces become zeroes, then non-numeric, zero and out-of-range values are rejected.
     */
    public MenuOption select(String role, String rawOption) {
        List<MenuOption> options = "A".equals(role) ? ADMIN_MENU : MAIN_MENU;
        String option = rawOption == null ? "" : rawOption.trim().replace(' ', '0');
        if (option.isEmpty() || !option.chars().allMatch(Character::isDigit)) {
            throw ApiException.badRequest("Please enter a valid option number...");
        }
        int number = Integer.parseInt(option);
        if (number < 1 || number > options.size()) {
            throw ApiException.badRequest("Please enter a valid option number...");
        }
        MenuOption selected = options.get(number - 1);
        if (!selected.installed()) {
            throw ApiException.badRequest(selected.name() + " is not available at this time ...");
        }
        if (!"A".equals(role) && "A".equals(selected.userType())) {
            throw ApiException.forbidden("No access - Admin Only option... ");
        }
        return selected;
    }
}
