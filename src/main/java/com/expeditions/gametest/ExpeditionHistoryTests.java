package com.expeditions.gametest;

import com.expeditions.core.*;
import com.expeditions.server.*;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("expeditions_prog")
@PrefixGameTestTemplate(false)
public final class ExpeditionHistoryTests {
    private static void route(GameTestHelper h, int offset) {
        var level=h.getLevel();
        for(int x=offset;x<=offset+32;x++)for(int z=offset;z<=offset+4;z++) {
            level.setBlockAndUpdate(new BlockPos(x,-61,z),Blocks.STONE.defaultBlockState());
            for(int y=-60;y<=-55;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        String json="""
                {"name":"Report Trial","dimension":"minecraft:overworld","tokens":20,
                 "checkpoints":[[%d,%d],[%d,%d]],"exit":[%d,%d]}
                """.formatted(offset+4,offset+2,offset+16,offset+2,offset+28,offset+2);
        ExpeditionDefinitions.reload(Map.of(ResourceLocation.parse("test:report"),JsonParser.parseString(json)));
    }
    private static void cleanup(ExpeditionManager manager, ServerPlayer... players) {
        for(var player:players)if(manager.activeRun(player.getUUID()).isPresent())manager.failRun(player);
        ExpeditionDefinitions.reload(Map.of());
    }
    @GameTest(template="arena",batch="history_records",timeoutTicks=300)
    public static void completionAndFailureRetainPersonalCreditsAcrossSave(GameTestHelper h) {
        var manager=ExpeditionManager.get(h.getLevel().getServer());var player=h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        try {
            route(h,3340);
            var first=ExpeditionGameTests.startExpedition(h,manager,player);
            manager.activateCheckpoint(player,ExpeditionManager.toPos(first.checkpoints().getFirst()));
            int kept=first.securedTokens(player.getUUID());manager.failRun(player,"abandoned");
            var failed=manager.savedData().history().entries(player.getUUID()).getFirst();
            h.assertTrue(failed.outcome()==ExpeditionStatus.FAILED && failed.tokensKept()==kept && kept>0,
                    "failure report must preserve checkpoint credits, not promise the lost reward");
            var second=ExpeditionGameTests.startExpedition(h,manager,player);
            for(var point:second.checkpoints())manager.activateCheckpoint(player,ExpeditionManager.toPos(point));
            manager.completeRun(player);
            var entries=manager.savedData().history().entries(player.getUUID());
            h.assertTrue(entries.size()==2,"a new run must not erase the previous personal report");
            var complete=entries.stream().filter(e->e.runId().equals(second.runId())).findFirst().orElseThrow();
            h.assertTrue(complete.outcome()==ExpeditionStatus.COMPLETED && complete.tokensKept()==second.tokensAwarded(),
                    "completion report records the full credited share, not just the extraction top-up");
            var saved=manager.savedData().save(new CompoundTag(),h.getLevel().registryAccess());
            var corrupt=new CompoundTag();corrupt.putString("Outcome","ACTIVE");
            saved.getList("History",Tag.TAG_COMPOUND).add(corrupt);
            var restored=ExpeditionSavedData.load(saved,h.getLevel().registryAccess());
            h.assertTrue(new HashSet<>(restored.history().entries(player.getUUID())).equals(new HashSet<>(entries)),
                    "valid reports must survive save/load even alongside a malformed report");
            h.assertTrue(ExpeditionSavedData.load(new CompoundTag(),h.getLevel().registryAccess()).history()
                    .entries(player.getUUID()).isEmpty(),"legacy saves must not fabricate reports");
        } finally {cleanup(manager,player);}
        h.succeed();
    }
    @GameTest(template="arena",batch="history_party",timeoutTicks=300)
    public static void departingMemberCanRejoinWithoutDuplicateOrStaleFailure(GameTestHelper h) {
        var manager=ExpeditionManager.get(h.getLevel().getServer());
        var owner=h.makeMockServerPlayerInLevel();var member=h.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.CREATIVE);member.setGameMode(GameType.CREATIVE);
        try {
            route(h,3400);var run=ExpeditionGameTests.startExpedition(h,manager,owner);
            var portal=ExpeditionManager.toPos(run.portal());manager.enterPortal(member,portal);
            manager.failRun(member,"abandoned");
            h.assertTrue(!manager.savedData().history().entries(member.getUUID()).getFirst().finalResult(),
                    "departing an open party records a replaceable personal withdrawal");
            manager.enterPortal(member,portal);
            for(var point:run.checkpoints())manager.activateCheckpoint(owner,ExpeditionManager.toPos(point));
            manager.completeRun(owner);
            var entries=manager.savedData().history().entries(member.getUUID());
            h.assertTrue(entries.size()==1 && entries.getFirst().outcome()==ExpeditionStatus.COMPLETED
                    && entries.getFirst().finalResult(),"extraction after rejoining replaces the old withdrawal");
            h.assertTrue(manager.savedData().history().entries(owner.getUUID()).getFirst().tokensKept()==run.tokensAwarded(),
                    "every party member receives an independent report of the actual share");
        } finally {cleanup(manager,owner,member);}
        h.succeed();
    }
    @GameTest(template="arena",batch="history_commands")
    public static void personalReportsAndCampaignCommandsWorkWithoutOperatorPermission(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
        var source=player.createCommandSourceStack().withPermission(0);
        for(String command:List.of("expeditions history","expeditions campaign","expeditions campaign all"))
            h.assertTrue(dispatcher.execute(command,source)==1,"public command must be available: "+command);
        h.succeed();
    }
}
