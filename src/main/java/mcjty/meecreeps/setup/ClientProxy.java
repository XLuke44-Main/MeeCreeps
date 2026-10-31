package mcjty.meecreeps.setup;

import mcjty.lib.setup.DefaultClientProxy;
import mcjty.meecreeps.entities.ModEntities;
import mcjty.meecreeps.input.KeyBindings;
import mcjty.meecreeps.input.KeyInputHandler;
import mcjty.meecreeps.render.BalloonRenderer;
import mcjty.meecreeps.gui.GuiBlenderGameOver;
import mcjty.meecreeps.teleport.BlenderDeathState;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends DefaultClientProxy {

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);

        MinecraftForge.EVENT_BUS.register(this);

        ModEntities.initModels();
    }

    @Override
    public void init(FMLInitializationEvent e) {
        super.init(e);
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
        KeyBindings.init();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiGameOver && BlenderDeathState.consumePending()) {
            event.setGui(new GuiBlenderGameOver());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void renderGameOverlayEvent(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }

        BalloonRenderer.renderBalloon();
    }
}
