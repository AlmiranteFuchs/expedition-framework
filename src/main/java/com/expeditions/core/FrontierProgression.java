package com.expeditions.core;

import java.util.HashSet;
import java.util.List;

/** Generated campaign missions use the same authoritative requirements and boss proof as authored ones. */
public final class FrontierProgression {
    private FrontierProgression() { }
    public static ExpeditionDefinition prepare(ExpeditionDefinition base,boolean homePrepared) {
        if(homePrepared || base.dimension().equals("minecraft:overworld")) return base;
        var requires=new HashSet<>(base.requires());requires.add("expeditions:field_clear");
        return new ExpeditionDefinition(base.id(),base.displayName(),base.difficulty(),base.dimension(),base.route(),
                base.tokenReward(),base.bonusTokens(),base.bonusTimeTicks(),base.objectives(),base.modifiers(),
                requires,base.grants(),base.costTokens(),base.minCampaignState(),base.bigBad(),base.commissionPool());
    }
    public static ExpeditionDefinition liberation(ExpeditionDefinition base) {
        var route=base.route() instanceof RouteGeneration g?g.withCheckpoints(3):base.route();
        return new ExpeditionDefinition("expeditions:liberation/"+base.dimension().replace(':','/'),
                "Sever the Bloom: "+base.displayName(),3,base.dimension(),route,Math.max(60,base.tokenReward()),
                20,20*900,List.of(),base.modifiers(),base.requires(),base.grants(),10,CampaignState.ESTABLISHED,true);
    }

    public static String aftergrowthId(String dimension) {
        return "expeditions:aftergrowth/" + dimension.replace(':', '/');
    }

    public static boolean isAftergrowth(ExpeditionDefinition definition) {
        return !definition.bigBad() && definition.minCampaignState() == CampaignState.CLEARED
                && definition.id().equals(aftergrowthId(definition.dimension()));
    }

    public static ExpeditionDefinition aftergrowth(String dimension) {
        return new ExpeditionDefinition(aftergrowthId(dimension), "Aftergrowth Survey", 0, dimension,
                new RouteGeneration(2, 28, 48, 28, 48, 6, 64, 128),
                4, 0, 0, List.of(), ExpeditionModifiers.NONE,
                java.util.Set.of(), java.util.Set.of(), 0, CampaignState.CLEARED, false);
    }
}
