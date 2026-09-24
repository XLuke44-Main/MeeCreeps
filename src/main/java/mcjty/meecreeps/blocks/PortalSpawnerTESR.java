package mcjty.meecreeps.blocks;

import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.items.TransportSolutionType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class PortalSpawnerTESR extends TileEntitySpecialRenderer<PortalSpawnerTileEntity> {

    private static final double SPAWNER_PLANE_OFFSET = 0.0075D;

    private static final ResourceLocation spawnerInterdimensional = new ResourceLocation(
            MeeCreeps.MODID, "textures/blocks/portal_spawner_interdimensional.png");
    private static final ResourceLocation spawnerIntradimensional = new ResourceLocation(
            MeeCreeps.MODID, "textures/blocks/portal_spawner_intradimensional.png");
    private static final ResourceLocation spawnerExtradimensional = new ResourceLocation(
            MeeCreeps.MODID, "textures/blocks/portal_spawner_extradimensional.png");
    private static final ResourceLocation spawnerHyperdimensional = new ResourceLocation(
            MeeCreeps.MODID, "textures/blocks/portal_spawner_hyperdimensional.png");

    @Override
    public void render(PortalSpawnerTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) {
            return;
        }

        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof PortalSpawnerBlock)
                || state.getValue(PortalSpawnerBlock.HALF) != PortalSpawnerBlock.Half.UPPER) {
            return;
        }

        PortalSpawnerBlock block = (PortalSpawnerBlock) state.getBlock();
        this.bindTexture(getSpawnerTexture(block.getTransportSolutionType()));

        EnumFacing facing = state.getValue(PortalSpawnerBlock.FACING);
        double plane = getSpawnerPlane(facing);
        EnumFacing right = facing.rotateY();
        double rx = right.getFrontOffsetX();
        double rz = right.getFrontOffsetZ();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y, (float) z);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01F);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            renderPanel(buffer, facing, rx, rz, plane, -1.0D, 0.0D);
            renderPanel(buffer, facing, rx, rz, plane, 0.0D, 1.0D);
            tessellator.draw();

            TileEntity portalTe = te.getWorld().getTileEntity(te.getPos().down());
            if (portalTe instanceof PortalTileEntity) {
                PortalTESR.renderSpawnerPortal((PortalTileEntity) portalTe, 0.0D, -1.0D, 0.0D);
            }
        } finally {
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
        }
    }

    private static double getSpawnerPlane(EnumFacing facing) {
        switch (facing) {
            case NORTH:
            case WEST:
                return 1.0D - SPAWNER_PLANE_OFFSET;
            case SOUTH:
            case EAST:
                return SPAWNER_PLANE_OFFSET;
            default:
                return SPAWNER_PLANE_OFFSET;
        }
    }

    private static void renderPanel(BufferBuilder buffer, EnumFacing facing,
                                    double rx, double rz, double plane,
                                    double minY, double maxY) {
        final double half = 0.5D;
        final double centerX = 0.5D;
        final double centerZ = 0.5D;
        final double leftX = centerX - rx * half;
        final double leftZ = centerZ - rz * half;
        final double rightX = centerX + rx * half;
        final double rightZ = centerZ + rz * half;

        switch (facing) {
            case NORTH:
            case SOUTH:
                buffer.pos(leftX, maxY, plane).tex(0.0D, 0.0D).endVertex();
                buffer.pos(leftX, minY, plane).tex(0.0D, 1.0D).endVertex();
                buffer.pos(rightX, minY, plane).tex(1.0D, 1.0D).endVertex();
                buffer.pos(rightX, maxY, plane).tex(1.0D, 0.0D).endVertex();
                break;
            case EAST:
            case WEST:
                buffer.pos(plane, maxY, leftZ).tex(0.0D, 0.0D).endVertex();
                buffer.pos(plane, minY, leftZ).tex(0.0D, 1.0D).endVertex();
                buffer.pos(plane, minY, rightZ).tex(1.0D, 1.0D).endVertex();
                buffer.pos(plane, maxY, rightZ).tex(1.0D, 0.0D).endVertex();
                break;
            default:
                break;
        }
    }

    private static ResourceLocation getSpawnerTexture(TransportSolutionType type) {
        switch (type) {
            case INTRADIMENSIONAL:
                return spawnerIntradimensional;
            case EXTRADIMENSIONAL:
                return spawnerExtradimensional;
            case HYPERDIMENSIONAL:
                return spawnerHyperdimensional;
            case INTERDIMENSIONAL:
            default:
                return spawnerInterdimensional;
        }
    }

    public static void register() {
        ClientRegistry.bindTileEntitySpecialRenderer(PortalSpawnerTileEntity.class, new PortalSpawnerTESR());
    }
}
