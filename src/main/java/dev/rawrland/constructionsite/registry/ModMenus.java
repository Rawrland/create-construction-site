package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.console.OperatorConsoleMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's windows that show items (menus). */
public final class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, CreateConstructionSite.MODID);

    /** The Operator Console's frequencies window. */
    public static final DeferredHolder<MenuType<?>, MenuType<OperatorConsoleMenu>> OPERATOR_CONSOLE =
        MENUS.register("operator_console", () -> IMenuTypeExtension.create(OperatorConsoleMenu::client));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
