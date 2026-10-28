package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.registry.ModItems;
import com.expeditions.server.data.ExpeditionTuning;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;

/**
 * The organism's second reach: infection. Pressure is the dimension's
 * immune response; infection is personal — lingering within
 * {@code microbiome.infectionRadius} of a live assimilation heart lets
 * the colony take hold of the player. It accrues on a cadence, decays
 * slowly away from hearts, and crosses staged thresholds — whispers
 * (darkness/nausea), grip (slowness/mining fatigue), takeover
 * (heavier slowness/weakness/poison) — until total assimilation replaces
 * the player with an effigy of themselves standing in the colony.
 *
 * <p>Warding delays but never immunizes: enchanted (imbued) armor and a
 * carried attunement sigil slow the accrual, capped at
 * {@code infectionWardMax}. A liberated dimension's dead organism can't
 * infect — accrual stops there entirely while existing infections still
 * fade on their own schedule.
 *
 * <p>The infection lives on the player's persistent NBT — it survives
 * death and relog because the fiction is that it's <em>in them</em>;
 * only assimilation itself (or decay to zero) clears it.
 */
public final class Assimilation {
    /** Persistent NBT key holding the infection value (double, 0..max). */
    private static final String TAG_INFECTION = "expeditions:assimilation";
    /** Entity tag marking the armor-stand effigies conversion leaves. */
    public static final String EFFIGY_TAG = "expeditions:assimilated";

    private static final ResourceKey<DamageType> ASSIMILATION_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("expeditions", "assimilation"));

    /**
     * Test-only seam: mock players stand near marker colonies for whole
     * tests and would be converted mid-assertion. Infection tests lift
     * this explicitly inside try/finally.
     */
    private static volatile boolean suppressedForTests;

    public static void suppressForTests(boolean suppressed) {
        suppressedForTests = suppressed;
    }

    private Assimilation() {
    }

    /** Current infection value for {@code player} (0..infectionMax). */
    public static double infection(ServerPlayer player) {
        return player.getPersistentData().getDouble(TAG_INFECTION);
    }

    /**
     * Sets the infection value directly — admin/test seam; conversion only
     * happens through {@link #onPlayerTick}, so forcing max here primes it.
     */
    public static void infect(ServerPlayer player, double value) {
        player.getPersistentData().putDouble(TAG_INFECTION, value);
    }

    /**
     * Per-player tick: on the configured cadence, accrue infection near a
     * live heart or decay it away from one, then re-apply the stage's
     * debuffs. Off-cadence ticks only keep effects fresh enough to never
     * gap — cheap since infected players are rare.
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (suppressedForTests || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int interval = ExpeditionTuning.integer(level.dimension(),
                "microbiome.infection_interval_ticks",
                ExpeditionsConfig.infectionIntervalTicks());
        double infection = infection(player);
        if (player.tickCount == 0 || player.tickCount % interval != 0) {
            // Between steps an infected player still needs effect refresh.
            if (infection > 0) {
                applyStageEffects(player, level, infection);
            }
            return;
        }
        double max = ExpeditionTuning.integer(level.dimension(),
                "microbiome.infection_max", ExpeditionsConfig.infectionMax());
        if (infection >= max) {
            return; // already converted; effigy handled at threshold crossing
        }
        if (nearHeart(level, player.blockPosition()) && Microbiome.enabled(level)) {
            double accrual = ExpeditionTuning.integer(level.dimension(),
                    "microbiome.infection_accrual",
                    ExpeditionsConfig.infectionAccrual());
            infection = Math.min(max, infection + accrual * wardingFactor(player));
        } else if (infection > 0) {
            infection = Math.max(0, infection - ExpeditionTuning.integer(
                    level.dimension(), "microbiome.infection_decay",
                    ExpeditionsConfig.infectionDecay()));
        }
        player.getPersistentData().putDouble(TAG_INFECTION, infection);
        if (infection >= max) {
            convert(player, level);
        } else if (infection > 0) {
            applyStageEffects(player, level, infection);
        }
    }

    /** Any live, loaded heart within infection radius of {@code pos}. */
    private static boolean nearHeart(ServerLevel level, BlockPos pos) {
        double radius = ExpeditionTuning.integer(level.dimension(),
                "microbiome.infection_radius", ExpeditionsConfig.infectionRadius());
        Set<BlockPos> hearts = AssimilationHeartBlockEntity.liveHearts(
                level.dimension().location().toString());
        for (BlockPos heart : hearts) {
            if (pos.distSqr(heart) <= radius * radius && level.isLoaded(heart)
                    && level.getBlockEntity(heart)
                            instanceof AssimilationHeartBlockEntity) {
                return true;
            }
        }
        return false;
    }

    /**
     * The player's warding: each enchanted armor piece delays, a carried
     * attunement sigil delays more — capped so the organism can always
     * finish taking a patient host.
     */
    private static double wardingFactor(ServerPlayer player) {
        int enchanted = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot).isEnchanted()) {
                enchanted++;
            }
        }
        boolean sigil = player.getInventory().items.stream()
                .anyMatch(s -> s.is(ModItems.ATTUNEMENT_SIGIL.get()));
        double ward = enchanted * ExpeditionsConfig.infectionWardPerEnchantedPiece()
                + (sigil ? ExpeditionsConfig.infectionWardSigil() : 0);
        return 1.0 - Math.min(ExpeditionsConfig.infectionWardMax(), ward);
    }

    /**
     * The stage's debuffs, re-applied every interval with enough duration
     * to bridge to the next step. Ambient + invisible icons — the
     * infection announces itself through the senses, not the HUD.
     */
    private static void applyStageEffects(ServerPlayer player, ServerLevel level,
            double infection) {
        int stage = stageOf(level, infection);
        if (stage >= 1) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, true, false));
        }
        if (stage >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 160, 0, true, false));
        }
        if (stage >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0, true, false));
        }
    }

    /** Infection's stage: 0 clean, 1 whispers, 2 grip, 3 takeover. */
    private static int stageOf(ServerLevel level, double infection) {
        if (infection >= ExpeditionTuning.integer(level.dimension(),
                "microbiome.infection_stage3_at", ExpeditionsConfig.infectionStage3At())) {
            return 3;
        }
        if (infection >= ExpeditionTuning.integer(level.dimension(),
                "microbiome.infection_stage2_at", ExpeditionsConfig.infectionStage2At())) {
            return 2;
        }
        if (infection >= ExpeditionTuning.integer(level.dimension(),
                "microbiome.infection_stage1_at", ExpeditionsConfig.infectionStage1At())) {
            return 1;
        }
        return 0;
    }

    /**
     * Total assimilation: an effigy of the player — their head, their
     * worn armor, their name — stands where the colony took them, and
     * the infection's own damage type finishes the body it consumed.
     * The effigy is an armor stand for now, tagged so a real model can
     * replace it later.
     */
    private static void convert(ServerPlayer player, ServerLevel level) {
        ArmorStand effigy = EntityType.ARMOR_STAND.create(level);
        if (effigy != null) {
            effigy.moveTo(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), 0.0F);
            effigy.setCustomName(Component.literal(
                    "Assimilated " + player.getGameProfile().getName()));
            effigy.setCustomNameVisible(true);
            effigy.setInvulnerable(true);
            effigy.setNoGravity(true);
            effigy.setShowArms(true);
            effigy.addTag(EFFIGY_TAG);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE,
                    new ResolvableProfile(player.getGameProfile()));
            effigy.setItemSlot(EquipmentSlot.HEAD, head);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack worn = player.getItemBySlot(slot);
                if (!worn.isEmpty()) {
                    effigy.setItemSlot(slot, worn.copy());
                    worn.setCount(0);
                }
            }
            level.addFreshEntity(effigy);
        }
        player.getPersistentData().putDouble(TAG_INFECTION, 0);
        player.hurt(player.damageSources().source(ASSIMILATION_DAMAGE),
                Float.MAX_VALUE);
    }
}
