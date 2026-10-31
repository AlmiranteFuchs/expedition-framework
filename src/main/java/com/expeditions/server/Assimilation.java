package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModItems;
import com.expeditions.registry.ModBlocks;
import com.expeditions.core.InfectionProgress;
import com.expeditions.core.BloomBoundary;
import com.expeditions.block.AssimilationConnections;
import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import com.expeditions.server.data.ExpeditionTuning;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;

/**
 * Server-authoritative contact infection. Live painted tissue and infected bodies
 * queue exposure; a cadence consumes it once, so brief contact is not missed.
 * Heart proximity never causes infection. Disruption blocks new exposure without
 * curing an existing dose. Native mobs acquire a persistent infected variant and
 * retain their species; players keep the established stage/effigy lifecycle.
 */
public final class Assimilation {
    /** Persistent NBT key holding the infection value (double, 0..max). */
    private static final String TAG_INFECTION = "expeditions:assimilation";
    /** Last announced stage — transitions whisper the escalation. */
    private static final String TAG_STAGE = "expeditions:assimilation_stage";
    private static final String TAG_CONTACT = "expeditions:assimilation_contact";
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
        suppressedForTests = suppressed && Boolean.getBoolean("expeditions.gameTestRun");
    }
    public static void onAttacked(Mob victim,LivingEntity attacker) {
        if(!suppressedForTests) HiveMind.alert(victim,attacker);
    }

    private Assimilation() {
    }

    /** Current infection value for {@code player} (0..infectionMax). */
    public static double infection(LivingEntity player) {
        return player.getPersistentData().getDouble(TAG_INFECTION);
    }

    /**
     * Sets the infection value directly — admin/test seam; conversion only
     * happens through {@link #onPlayerTick}, so forcing max here primes it.
     */
    public static void infect(LivingEntity player, double value) {
        player.getPersistentData().putDouble(TAG_INFECTION, value);
    }

    /**
     * Per-player tick: consume queued physical exposure on cadence,
     * or let existing exposure decay, then re-apply the stage's
     * debuffs. Off-cadence ticks only keep effects fresh enough to never
     * gap — cheap since infected players are rare.
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (suppressedForTests || player.isSpectator()) {
            return;
        }
        // Remove the old infection's hidden sensory effects on existing saves.
        // Ordinary potion/warden effects have different flags and are preserved.
        if (infection(player) > 0 || player.getPersistentData().getInt(TAG_STAGE) > 0) {
            clearLegacySensoryEffect(player, MobEffects.CONFUSION);
            clearLegacySensoryEffect(player, MobEffects.DARKNESS);
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
        infection = advance(player,level,0,wardingFactor(player));
        player.getPersistentData().putDouble(TAG_INFECTION, infection);
        syncOverlayFlag(player, infection > 0 ? stageOf(level, infection) : 0);
        if (infection >= max) {
            convert(player, level);
        } else if (infection > 0) {
            applyStageEffects(player, level, infection);
        }
        announceStage(player, level, infection);
    }

    /**
     * The felt layer: crossing a stage threshold whispers the escalation
     * in chat, and every cadence tick an infected player hears the
     * organism's heartbeat from the inside — faint at first touch,
     * pounding by takeover. No feedback is the bug players reported;
     * the Bloom should never be silent once it has hold.
     */
    private static void announceStage(ServerPlayer player, ServerLevel level,
            double infection) {
        int stage = stageOf(level, infection);
        int lastStage = player.getPersistentData().getInt(TAG_STAGE);
        if (stage != lastStage) {
            player.getPersistentData().putInt(TAG_STAGE, stage);
            if (stage > lastStage && stage >= 1) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.assimilation_stage" + stage));
            } else if (stage == 0 && lastStage > 0) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.assimilation_fades"));
            }
        }
        if (infection > 0) {
            player.playNotifySound(net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.25f + stage * 0.2f, 1.0f + stage * 0.08f);
        }
    }

    /** Queue a real touch until the next cadence, so brief crossings are not missed. */
    public static void onContact(LivingEntity host) {
        if(suppressedForTests || !(host.level() instanceof ServerLevel level) || !host.isAlive()
                || host instanceof ArmorStand || host.isSpectator() || !Microbiome.enabled(level)
                || AssimilationProtection.protectedFromInfection(host)) return;
        host.getPersistentData().putBoolean(TAG_CONTACT,true);
    }

    /** The actual painted surface, including its frayed boundary, is the contact volume. */
    public static void touchLayer(ServerLevel level,BlockPos pos,BlockState state,LivingEntity host) {
        if(level.getBlockEntity(pos) instanceof AssimilationLayerBlockEntity layer && layer.heart()!=null
                && (!level.isLoaded(layer.heart()) || !level.getBlockState(layer.heart()).is(ModBlocks.ASSIMILATION_HEART.get()))) return;
        AABB body=host.getBoundingBox().move(-pos.getX(),-pos.getY(),-pos.getZ());
        for(Direction support:MultifaceBlock.availableFaces(state)) {
            Direction normal=support.getOpposite();
            var mask=BloomBoundary.regions(AssimilationConnections.boundaryMask(level,pos,normal),
                    (int)(net.minecraft.util.Mth.getSeed(pos)&3));
            for(var r:mask) {
                double u0=r.x0()/8.0,u1=r.x1()/8.0,v0=r.y0()/8.0,v1=r.y1()/8.0,t=1.0/16;
                AABB face=switch(normal) {
                    case UP -> new AABB(u0,0,v0,u1,t,v1);
                    case DOWN -> new AABB(u0,1-t,v0,u1,1,v1);
                    case NORTH -> new AABB(1-u1,1-v1,1-t,1-u0,1-v0,1);
                    case SOUTH -> new AABB(u0,1-v1,0,u1,1-v0,t);
                    case WEST -> new AABB(1-t,1-v1,1-u1,1,1-v0,1-u0);
                    case EAST -> new AABB(0,1-v1,u0,t,1-v0,u1);
                };
                if(body.intersects(face)) { onContact(host); return; }
            }
        }
    }

    private static double advance(LivingEntity host,ServerLevel level,double floor,double warding) {
        boolean contact=host.getPersistentData().getBoolean(TAG_CONTACT) && Microbiome.enabled(level);
        host.getPersistentData().remove(TAG_CONTACT);
        double accrual=ExpeditionTuning.integer(level.dimension(),"microbiome.infection_accrual",ExpeditionsConfig.infectionAccrual());
        double decay=ExpeditionTuning.integer(level.dimension(),"microbiome.infection_decay",ExpeditionsConfig.infectionDecay());
        double maximum=ExpeditionTuning.integer(level.dimension(),"microbiome.infection_max",ExpeditionsConfig.infectionMax());
        return InfectionProgress.step(infection(host),contact,AssimilationProtection.protectedFromInfection(host),
                accrual*warding,decay,maximum,Math.min(floor,maximum));
    }

    /** Non-player hosts keep their native entity type and acquire the persistent infected variant. */
    public static void onLivingTick(LivingEntity entity) {
        AssimilationProtection.tick(entity);
        if(suppressedForTests || !(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        if(entity instanceof Mob mob) {
            int flag=mob.getData(ModAttachments.ASSIMILATED);
            if(flag>0 && infection(mob)<threshold(level,flag)) infect(mob,threshold(level,flag));
            int interval=ExpeditionTuning.integer(level.dimension(),"microbiome.infection_interval_ticks",ExpeditionsConfig.infectionIntervalTicks());
            if(mob.tickCount>0 && mob.tickCount%interval==0) {
                double value=advance(mob,level,flag>0 && Microbiome.enabled(level)?threshold(level,1):0,1);
                infect(mob,value); int stage=value>0?stageOf(level,value):0;
                if(stage!=flag) mob.setData(ModAttachments.ASSIMILATED,stage);
            }
            HiveMind.tick(mob);
        }
        if(entity.getData(ModAttachments.ASSIMILATED)>0 && (entity.tickCount+entity.getId())%4==0) {
            AABB touch=entity.getBoundingBox().inflate(1.0/64);
            for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,touch,
                    e->e!=entity && e.isAlive() && !(e instanceof ArmorStand))) onContact(other);
        }
    }

    private static int threshold(ServerLevel level,int stage) {
        return ExpeditionTuning.integer(level.dimension(),"microbiome.infection_stage"+stage+"_at",
                stage==1?ExpeditionsConfig.infectionStage1At():stage==2?ExpeditionsConfig.infectionStage2At():ExpeditionsConfig.infectionStage3At());
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

    private static void clearLegacySensoryEffect(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type) {
        var effect = player.getEffect(type);
        if (effect != null && effect.isAmbient() && !effect.isVisible() && !effect.showIcon()
                && effect.getAmplifier() == 0 && effect.getDuration() <= 160) player.removeEffect(type);
    }

    /**
     * Mirrors the infection stage onto the synced attachment so the client
     * overlay and ember particles show the take on the player model —
     * written only on change to keep sync traffic quiet.
     */
    private static void syncOverlayFlag(ServerPlayer player, int stage) {
        if (player.getData(ModAttachments.ASSIMILATED) != stage) {
            player.setData(ModAttachments.ASSIMILATED, stage);
        }
    }

    /** The player's infection stage: 0 clean, 1 whispers, 2 grip, 3 takeover. */
    public static int stage(ServerPlayer player) {
        return stageOf(player.serverLevel(), infection(player));
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
        player.getPersistentData().putInt(TAG_STAGE, 0);
        player.hurt(player.damageSources().source(ASSIMILATION_DAMAGE),
                Float.MAX_VALUE);
    }
}
