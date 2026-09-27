package mcjty.meecreeps.input;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class KeyBindings {

    public static KeyBinding repeatLastMessage;
    public static KeyBinding fastReturn;
    public static KeyBinding autoClose;
    public static KeyBinding closeAllPortals;
    public static KeyBinding openSavedDestinations;
    public static KeyBinding airPlacement;

    public static void init() {
        repeatLastMessage = new KeyBinding("key.last_message", KeyConflictContext.IN_GAME, Keyboard.KEY_R, "key.categories.meecreeps");
        fastReturn = new KeyBinding("key.fast_return", KeyConflictContext.UNIVERSAL, Keyboard.KEY_L, "key.categories.meecreeps");
        autoClose = new KeyBinding("key.portal_autoclose", KeyConflictContext.UNIVERSAL, Keyboard.KEY_C, "key.categories.meecreeps");
        closeAllPortals = new KeyBinding("key.close_all_portals", KeyConflictContext.UNIVERSAL, Keyboard.KEY_J, "key.categories.meecreeps");
        openSavedDestinations = new KeyBinding("key.open_saved_destinations", KeyConflictContext.IN_GAME, Keyboard.KEY_G, "key.categories.meecreeps");
        airPlacement = new KeyBinding("key.air_placement", KeyConflictContext.IN_GAME, Keyboard.KEY_H, "key.categories.meecreeps");
        ClientRegistry.registerKeyBinding(repeatLastMessage);
        ClientRegistry.registerKeyBinding(fastReturn);
        ClientRegistry.registerKeyBinding(autoClose);
        ClientRegistry.registerKeyBinding(closeAllPortals);
        ClientRegistry.registerKeyBinding(openSavedDestinations);
        ClientRegistry.registerKeyBinding(airPlacement);
    }
}
