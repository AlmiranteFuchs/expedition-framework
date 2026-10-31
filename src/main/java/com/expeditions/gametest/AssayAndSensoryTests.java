package com.expeditions.gametest;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.item.FieldAssayKitItem;
import com.expeditions.registry.ModItems;
import com.expeditions.server.Assimilation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_journal")
@PrefixGameTestTemplate(false)
public final class AssayAndSensoryTests {
    private AssayAndSensoryTests() { }
    @GameTest(template="arena",batch="assay_nonlethal")
    public static void aCriticalPatientCannotPayForASample(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var kit=new ItemStack(ModItems.FIELD_ASSAY_KIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,kit); player.setHealth(2); player.setShiftKeyDown(true);
        ModItems.FIELD_ASSAY_KIT.get().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getHealth()==2 && player.isAlive() && FieldAssayKitItem.record(kit).isEmpty(),
                "critical health must refuse sampling without damage or a fabricated result");
        player.setShiftKeyDown(false); helper.succeed();
    }
    @GameTest(template="arena",batch="infection_sensory_migration")
    public static void oldInfectionEffectsClearWithoutRemovingOtherSources(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        try {
            Assimilation.suppressForTests(false);
            Assimilation.infect(player,ExpeditionsConfig.infectionStage1At());
            player.tickCount=1;
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,160,0,true,false));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,160,0,true,false));
            Assimilation.onPlayerTick(player);
            helper.assertTrue(!player.hasEffect(MobEffects.CONFUSION) && !player.hasEffect(MobEffects.DARKNESS),
                    "legacy hidden infection effects must clear and never refresh");
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,240,0,false,false));
            Assimilation.onPlayerTick(player);
            helper.assertTrue(player.hasEffect(MobEffects.DARKNESS),"a different source's darkness must survive migration");
        } finally {
            Assimilation.suppressForTests(true); Assimilation.infect(player,0);
        }
        helper.succeed();
    }
}
