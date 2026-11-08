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
    public static KeyBinding favouriteDestination;
    public static KeyBinding togglePortalGunMode;
    public static KeyBinding terminateCube;

    public static void init() {
        repeatLastMessage = new KeyBinding("key.last_message", KeyConflictContext.UNIVERSAL, Keyboard.KEY_NONE, "key.categories.meecreeps");
        fastReturn = new KeyBinding("key.fast_return", KeyConflictContext.UNIVERSAL, Keyboard.KEY_L, "key.categories.meecreeps");
        autoClose = new KeyBinding("key.portal_autoclose", KeyConflictContext.UNIVERSAL, Keyboard.KEY_C, "key.categories.meecreeps");
        closeAllPortals = new KeyBinding("key.close_all_portals", KeyConflictContext.UNIVERSAL, Keyboard.KEY_J, "key.categories.meecreeps");
        openSavedDestinations = new KeyBinding("key.open_saved_destinations", KeyConflictContext.UNIVERSAL, Keyboard.KEY_Q, "key.categories.meecreeps");
        airPlacement = new KeyBinding("key.air_placement", KeyConflictContext.UNIVERSAL, Keyboard.KEY_R, "key.categories.meecreeps");
        favouriteDestination = new KeyBinding("key.favourite_destination", KeyConflictContext.UNIVERSAL, Keyboard.KEY_X, "key.categories.meecreeps");
        togglePortalGunMode = new KeyBinding("key.portal_gun_mode", KeyConflictContext.UNIVERSAL, Keyboard.KEY_M, "key.categories.meecreeps");
        terminateCube = new KeyBinding("key.terminate_cube", KeyConflictContext.UNIVERSAL, Keyboard.KEY_J, "key.categories.meecreeps");
        ClientRegistry.registerKeyBinding(repeatLastMessage);
        ClientRegistry.registerKeyBinding(fastReturn);
        ClientRegistry.registerKeyBinding(autoClose);
        ClientRegistry.registerKeyBinding(closeAllPortals);
        ClientRegistry.registerKeyBinding(openSavedDestinations);
        ClientRegistry.registerKeyBinding(airPlacement);
        ClientRegistry.registerKeyBinding(favouriteDestination);
        ClientRegistry.registerKeyBinding(togglePortalGunMode);
        ClientRegistry.registerKeyBinding(terminateCube);
    }
}
