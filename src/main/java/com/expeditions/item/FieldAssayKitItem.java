package com.expeditions.item;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.JournalChapter;
import com.expeditions.server.Assimilation;
import com.expeditions.server.JournalDiscovery;
import com.expeditions.server.data.ExpeditionTuning;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A physical blood sample and its measured result, separate from guidebook knowledge. */
public final class FieldAssayKitItem extends Item {
    private static final String RECORD = "expeditions:assay";
    private static java.util.function.Consumer<InteractionHand> clientOpener = hand -> { };
    public FieldAssayKitItem(Properties properties) { super(properties); }
    public static void setClientOpener(java.util.function.Consumer<InteractionHand> opener) { clientOpener = opener; }
    public static CompoundTag record(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompound(RECORD);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            if (!player.isShiftKeyDown()) clientOpener.accept(hand);
            return InteractionResultHolder.success(stack);
        }
        if (!player.isShiftKeyDown()) {
            player.containerMenu.broadcastChanges();
            return InteractionResultHolder.success(stack);
        }
        if (server.getHealth() <= 2) {
            server.sendSystemMessage(Component.literal("Too weak to draw blood safely. Recover above one heart first."));
            return InteractionResultHolder.fail(stack);
        }
        float before = server.getHealth() + server.getAbsorptionAmount();
        server.hurt(server.damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.parse("expeditions:blood_letting"))), 2);
        if (server.getHealth() + server.getAbsorptionAmount() >= before) {
            server.sendSystemMessage(Component.literal("Hold still before drawing another sample."));
            return InteractionResultHolder.fail(stack);
        }
        int max = Math.max(1, ExpeditionTuning.integer(level.dimension(), "microbiome.infection_max", ExpeditionsConfig.infectionMax()));
        CompoundTag record = new CompoundTag();
        record.putLong("Time", level.getGameTime());
        record.putString("Patient", server.getGameProfile().getName());
        record.putString("Dimension", level.dimension().location().toString());
        record.putInt("Percent", (int)Math.clamp(Math.round(Assimilation.infection(server) * 100 / max), 0, 100));
        record.putInt("Stage", Assimilation.stage(server));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(RECORD, record));
        JournalDiscovery.unlock(server, JournalChapter.BLOOD);
        server.getCooldowns().addCooldown(this, 20);
        server.containerMenu.broadcastChanges();
        server.sendSystemMessage(Component.literal("Sample sealed. Use the assay kit to inspect its result."));
        return InteractionResultHolder.success(stack);
    }
}
