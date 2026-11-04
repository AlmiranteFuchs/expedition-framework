package com.expeditions.entity;

import com.expeditions.core.GuardianAttack;
import com.expeditions.server.ExpeditionManager;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A rooted, telegraphed two-phase encounter. It never edits the arena terrain. */
public final class RootboundGuardian extends Monster {
    private static final EntityDataAccessor<Integer> WINDUP = SynchedEntityData.defineId(
            RootboundGuardian.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(
            RootboundGuardian.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(
            RootboundGuardian.class, EntityDataSerializers.BOOLEAN);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.expeditions.rootbound_guardian"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private Vec3 strike = Vec3.ZERO;
    private BlockPos anchor;
    private int recovery = 40;
    private int sequence;

    public RootboundGuardian(EntityType<? extends RootboundGuardian> type, Level level) {
        super(type, level);
        xpReward = 40;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 180)
                .add(Attributes.MOVEMENT_SPEED, 0.17).add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 5);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WINDUP, 0); builder.define(ATTACK, 0); builder.define(ENRAGED, false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PursueGoal());
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 24));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public void setArena(BlockPos pos) { anchor = pos.immutable(); }
    public int windupTicks() { return entityData.get(WINDUP); }
    public boolean enraged() { return entityData.get(ENRAGED); }
    public GuardianAttack attack() {
        return switch (entityData.get(ATTACK)) {
            case 1 -> GuardianAttack.ROOT_BURST;
            case 2 -> GuardianAttack.ROOT_RING;
            default -> GuardianAttack.SWEEP;
        };
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || !isAlive() || isNoAi()) return;
        bossBar.setName(getPersistentData().hasUUID(com.expeditions.server.FrontierThreat.OWNER)
                ?Component.translatable("frontier.expeditions.pursuer_name"):Component.translatable("entity.expeditions.rootbound_guardian"));
        bossBar.setProgress(Math.max(0, getHealth() / getMaxHealth()));
        bossBar.setColor(enraged() ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        if (!enraged() && getHealth() <= getMaxHealth() * 0.5f) {
            entityData.set(ENRAGED, true);
            bossBar.setColor(BossEvent.BossBarColor.YELLOW);
            server.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8f, 0.65f);
            server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.8, getZ(), 35, 0.7, 1.1, 0.7, 0.02);
        }
        int windup = windupTicks();
        if (windup > 0) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (windup % 4 == 0) warning(server);
            entityData.set(WINDUP, windup - 1);
            if (windup == 1) {
                strike(server);
                recovery = attack().recoveryTicks();
            }
            return;
        }
        if (recovery > 0) recovery--;
        var target = getTarget();
        if (target == null || !target.isAlive() || !hasLineOfSight(target)) return;
        if (anchor != null && target.blockPosition().distSqr(anchor) > 28 * 28) return;
        var next = GuardianAttack.next(enraged(), sequence);
        double range = next.triggerRange();
        if (recovery == 0 && distanceToSqr(target) <= range * range) {
            entityData.set(ATTACK, next.ordinal());
            strike = next == GuardianAttack.ROOT_BURST ? target.position() : position();
            entityData.set(WINDUP, next.warningTicks());
            sequence++;
            server.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.HOSTILE, 1.1f, switch (next) {
                        case SWEEP -> 0.55f;
                        case ROOT_BURST -> 0.9f;
                        case ROOT_RING -> 0.4f;
                    });
            warning(server);
        }
    }

    private void warning(ServerLevel level) {
        double r = attack().radius();
        drawRing(level, r, ParticleTypes.SMALL_FLAME);
        if (attack().innerRadius() > 0) {
            drawRing(level, attack().innerRadius(), ParticleTypes.ASH);
        } else {
            level.sendParticles(ParticleTypes.ASH, strike.x, strike.y + 0.3, strike.z, 8,
                    r * 0.35, 0.3, r * 0.35, 0.01);
        }
    }

    private void drawRing(ServerLevel level, double radius, net.minecraft.core.particles.SimpleParticleType particle) {
        for (int i = 0; i < 36; i++) {
            double angle = i * Math.PI * 2 / 36;
            level.sendParticles(particle, strike.x + Math.cos(angle) * radius,
                    strike.y + 0.12, strike.z + Math.sin(angle) * radius, 1, 0, 0.03, 0, 0);
        }
    }

    private void strike(ServerLevel level) {
        double r = attack().radius();
        level.playSound(null, BlockPos.containing(strike), SoundEvents.IRON_GOLEM_ATTACK,
                SoundSource.HOSTILE, 1.6f, 0.55f);
        if (attack() == GuardianAttack.ROOT_RING) {
            drawRing(level, (r + attack().innerRadius()) / 2, ParticleTypes.POOF);
        } else {
            level.sendParticles(ParticleTypes.POOF, strike.x, strike.y + 0.2, strike.z, 45, r * 0.5, 0.2, r * 0.5, 0.05);
            level.sendParticles(ParticleTypes.FLAME, strike.x, strike.y + 0.4, strike.z, 25, r * 0.45, 0.45, r * 0.45, 0.03);
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || !hasLineOfSight(player)) continue;
            Vec3 gap = player.position().subtract(strike);
            if (attack().hits(gap.x, gap.y, gap.z)) {
                if (player.hurt(damageSources().mobAttack(this), enraged() ? 10 : 8)) {
                    player.knockback(0.65, strike.x - player.getX(), strike.z - player.getZ());
                }
            }
        }
    }

    /** The exposed core rewards attacking during a readable, stationary windup. */
    @Override public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, windupTicks() > 0 ? amount * 1.5f : amount);
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (dead && level() instanceof ServerLevel server) {
            ExpeditionManager.get(server.getServer()).onGuardianDefeated(this);
            bossBar.removeAllPlayers();
        }
    }

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public void remove(RemovalReason reason) { bossBar.removeAllPlayers(); super.remove(reason); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOOD_BREAK; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchor != null) tag.putLong("Arena", anchor.asLong());
        tag.putBoolean("Enraged", enraged());
        tag.putInt("AttackSequence", sequence);
        // Reload cancels an interrupted windup and gives a fresh recovery window.
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Arena")) anchor = BlockPos.of(tag.getLong("Arena"));
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        sequence = tag.getInt("AttackSequence");
        entityData.set(WINDUP, 0); recovery = 40;
    }

    private final class PursueGoal extends Goal {
        PursueGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return getTarget() != null && getTarget().isAlive(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            var target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 25, 25);
            if (windupTicks() > 0) { getNavigation().stop(); return; }
            if (anchor != null && (blockPosition().distSqr(anchor) > 26 * 26
                    || target.blockPosition().distSqr(anchor) > 28 * 28)) {
                getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1);
            } else if (tickCount % 10 == 0) {
                double range = GuardianAttack.next(enraged(), sequence).triggerRange();
                if (distanceToSqr(target) <= range * range && hasLineOfSight(target)) getNavigation().stop();
                else getNavigation().moveTo(target, 1);
            }
        }
    }
}
