package dev.minecraftempires.client;

import dev.minecraftempires.common.entity.CitizenEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Temporary vanilla villager appearance until species-specific models arrive. */
public final class CitizenRenderer extends MobRenderer<CitizenEntity, VillagerModel<CitizenEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "minecraft", "textures/entity/villager/villager.png");

    public CitizenRenderer(final EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(final CitizenEntity entity) {
        return TEXTURE;
    }
}
