package com.expeditions.server;

import net.minecraft.world.entity.LivingEntity;

/** Transient, server-only attention. Never serialized or shared across worlds. */
public record HiveFocus(LivingEntity target,long refreshAt,long expiresAt) {
    public static final HiveFocus EMPTY=new HiveFocus(null,0,0);
}
