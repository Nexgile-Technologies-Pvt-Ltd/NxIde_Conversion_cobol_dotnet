package com.carddemo.web;

import com.carddemo.common.ApiException;
import com.carddemo.dto.OperationsDtos.MenuOption;
import com.carddemo.dto.OperationsDtos.MenuView;
import com.carddemo.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Menus. COBOL sources {@code COMEN01C} ({@code CM00}) and {@code COADM01C} ({@code CA00}). */
@RestController
@RequestMapping("/api/menu")
@Tag(name = "Menus", description = "CM00 main menu and CA00 administrator menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/main")
    @Operation(summary = "Main menu options (COMEN02Y)")
    public MenuView main() {
        return menuService.mainMenu(CurrentUser.role());
    }

    @GetMapping("/admin")
    @Operation(summary = "Administrator menu options (COADM02Y)")
    public MenuView admin() {
        if (!CurrentUser.isAdmin()) {
            throw ApiException.forbidden("You are not authorized to use this function ...");
        }
        return menuService.adminMenu(CurrentUser.role());
    }

    @GetMapping("/select")
    @Operation(summary = "Resolve a typed option number to its route, using the legacy edit rules")
    public MenuOption select(@RequestParam("option") String option) {
        return menuService.select(CurrentUser.role(), option);
    }
}
