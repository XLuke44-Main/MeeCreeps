package mcjty.meecreeps.teleport;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public final class BlenderDamageSource extends DamageSource {

    public static final BlenderDamageSource BLENDER = new BlenderDamageSource();

    private BlenderDamageSource() {
        super("blender");
        setDamageBypassesArmor();
        setDamageAllowedInCreativeMode();
    }

    @Override
    public ITextComponent getDeathMessage(EntityLivingBase entity) {
        return new TextComponentTranslation("death.attack.blender", entity.getDisplayName());
    }
}
