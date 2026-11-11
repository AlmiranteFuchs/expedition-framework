package com.expeditions.entity;

import com.expeditions.block.entity.FieldWardBlockEntity;
import com.expeditions.registry.ModEntities;
import com.expeditions.registry.ModSounds;
import com.expeditions.server.HiveMind;
import com.expeditions.server.MobSpawns;
import com.expeditions.server.Stillness;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A rooted scout. Calls existing infected life; never creates a reinforcement wave. */
public final class BloomListener extends Monster {
    public static final int WARNING_TICKS = 60;
    public static final int CALL_COOLDOWN = 300;
    private static final EntityDataAccessor<Integer> WARNING = SynchedEntityData.defineId(
            BloomListener.class, EntityDataSerializers.INT);
    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(.68f, .40f, .17f), .7f);
    private final DynamicGameEventListener<SoundListener> ears = new DynamicGameEventListener<>(new SoundListener());
    private Vec3 disturbance;
    private LivingEntity source;
    private int cooldown;
    private float previousOpening;
    private float opening;

    public BloomListener(EntityType<? extends BloomListener> type, Level level) { super(type, level); xpReward = 3; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, .16).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WARNING, 0);
    }

    public int warningTicks() { return entityData.get(WARNING); }
    public float opening(float partialTick) { return net.minecraft.util.Mth.lerp(partialTick, previousOpening, opening); }

    @Override public void updateDynamicGameEventListener(
            BiConsumer<DynamicGameEventListener<?>, ServerLevel> consumer) {
        if (level() instanceof ServerLevel server) consumer.accept(ears, server);
    }

    public GameEventListener soundListener() { return ears.getListener(); }

    private boolean sheltered() {
        return level() instanceof ServerLevel server && (Stillness.suppresses(server, blockPosition())
                || FieldWardBlockEntity.covers(server, blockPosition()));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            previousOpening = opening;
            opening = net.minecraft.util.Mth.approach(opening,
                    warningTicks() > 0 ? 1 : 0, warningTicks() > 0 ? 1f / WARNING_TICKS : .08f);
            return;
        }
        if (!isAlive() || isNoAi()) return;
        if (cooldown > 0) cooldown--;
        if (warningTicks() <= 0) return;
        if (sheltered()) { interrupt(); return; }
        getNavigation().stop();
        if (disturbance != null) getLookControl().setLookAt(disturbance.x, disturbance.y, disturbance.z, 20, 20);
        int remaining = warningTicks() - 1;
        entityData.set(WARNING, remaining);
        if (remaining == 0) callHive();
        else if (remaining % 10 == 0 && level() instanceof ServerLevel server)
            server.sendParticles(AMBER, getX(), getEyeY(), getZ(), 2, .15, .12, .15, 0);
    }

    private void callHive() {
        cooldown = CALL_COOLDOWN;
        if (!(level() instanceof ServerLevel server) || disturbance == null) return;
        playSound(ModSounds.LISTENER_CALL.get(), .75f, .9f);
        for (Mob peer : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24),
                mob -> !(mob instanceof BloomListener) && HiveMind.active(mob) && distanceToSqr(mob) <= 24 * 24)) {
            if (source != null && HiveMind.validHost(peer, source)
                    && !(source instanceof Player player && player.isCreative())) {
                HiveMind.alert(peer, source);
            } else if (peer.getTarget() == null) {
                InvestigateCallGoal.direct(peer, disturbance, server.getGameTime() + 200);
            }
        }
        server.sendParticles(AMBER, getX(), getEyeY(), getZ(), 12, .35, .18, .35, .02);
        disturbance = null;
        source = null;
    }

    private void interrupt() {
        entityData.set(WARNING, 0);
        cooldown = 160;
        disturbance = null;
        source = null;
    }

    @Override public boolean hurt(DamageSource damage, float amount) {
        boolean hit = super.hurt(damage, amount);
        if (hit && !level().isClientSide) interrupt();
        return hit;
    }

    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.BLOOM_SOURCE_CLOSE.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BLOOM_SOURCE_CLOSE.get(); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ListenerCooldown", Math.max(cooldown, warningTicks() > 0 ? 160 : 0));
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cooldown = Math.clamp(tag.getInt("ListenerCooldown"), 0, CALL_COOLDOWN);
        entityData.set(WARNING, 0);
        disturbance = null;
        source = null;
    }

    /** Called only from a growing colony, with bounded placement and local population. */
    public static void trySpawnNearColony(ServerLevel level, BlockPos heart) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || level.random.nextInt(8) != 0
                || Stillness.suppresses(level, heart) || FieldWardBlockEntity.covers(level, heart)) return;
        if (level.getNearestPlayer(heart.getX(), heart.getY(), heart.getZ(), 48, true) == null
                || !level.getEntitiesOfClass(BloomListener.class, new net.minecraft.world.phys.AABB(heart).inflate(24),
                        LivingEntity::isAlive).isEmpty()) return;
        Mob placed = MobSpawns.trySpawn(ModEntities.BLOOM_LISTENER.get(), level, heart, 6, 12);
        if (placed != null && level.players().stream().noneMatch(player -> !player.isSpectator()
                && placed.distanceToSqr(player) < 12 * 12)
                && !FieldWardBlockEntity.covers(level, placed.blockPosition())
                && !Stillness.suppresses(level, placed.blockPosition())) level.addFreshEntity(placed);
    }

    private final class SoundListener implements GameEventListener {
        private final PositionSource position = new EntityPositionSource(BloomListener.this, .65f);
        @Override public PositionSource getListenerSource() { return position; }
        @Override public int getListenerRadius() { return 16; }
        @Override public boolean handleGameEvent(ServerLevel server, Holder<GameEvent> event,
                GameEvent.Context context, Vec3 at) {
            if (!isAlive() || isNoAi() || cooldown > 0 || warningTicks() > 0 || sheltered()
                    || position().distanceToSqr(at) > 16 * 16
                    || FieldWardBlockEntity.covers(server, BlockPos.containing(at))) return false;
            boolean steps = event.is(GameEvent.STEP);
            boolean projectile = event.is(GameEvent.PROJECTILE_LAND);
            if (!(steps || projectile || event.is(GameEvent.BLOCK_DESTROY) || event.is(GameEvent.BLOCK_PLACE)
                    || event.is(GameEvent.BLOCK_ACTIVATE) || event.is(GameEvent.EXPLODE))) return false;
            var actor = context.sourceEntity();
            if (actor == BloomListener.this || actor != null && actor.isSpectator()
                    || actor instanceof Player player && player.isCreative()
                    || actor instanceof Mob mob && HiveMind.infected(mob)) return false;
            if (steps && (actor == null || actor.isSteppingCarefully())) return false;
            if (context.affectedState() != null && context.affectedState().is(BlockTags.DAMPENS_VIBRATIONS)) return false;
            if (server.isBlockInLine(new ClipBlockStateContext(at, getEyePosition(),
                    state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType() != HitResult.Type.MISS) return false;
            disturbance = at;
            source = !projectile && actor instanceof LivingEntity living ? living : null;
            entityData.set(WARNING, WARNING_TICKS);
            if (actor instanceof net.minecraft.server.level.ServerPlayer player)
                com.expeditions.server.JournalDiscovery.unlock(player, com.expeditions.core.JournalChapter.BLOOM);
            server.playSound(null, blockPosition(), ModSounds.LISTENER_WARNING.get(), SoundSource.HOSTILE, .55f, .9f);
            return true;
        }
    }
}
