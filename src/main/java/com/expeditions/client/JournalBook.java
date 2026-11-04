package com.expeditions.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import vazkii.patchouli.api.PatchouliAPI;

/** Presentation only. Discovery bits and the blooded stack remain server-owned. */
public final class JournalBook {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("expeditions", "field_guide");
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private JournalBook() { }
    public static InteractionHand hand() { return hand; }
    public static void open(InteractionHand usedHand) {
        hand = usedHand;
        PatchouliAPI.get().openBookGUI(ID);
    }
}
