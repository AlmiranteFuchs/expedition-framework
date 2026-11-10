package com.expeditions.server;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.FrontierProgression;
import com.expeditions.item.ReturnKnotItem;
import com.expeditions.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-owned snapshot of a player's extraction reward, independent of later pool changes. */
public record ReturnCache(UUID run, String name, String dimension, List<ItemStack> haul, List<ItemStack> choices) {
    public ReturnCache {
        if (ResourceLocation.tryParse(dimension) == null || haul.isEmpty() || haul.size() > 8 || choices.size() != 3
                || haul.stream().anyMatch(ItemStack::isEmpty) || choices.stream().anyMatch(ItemStack::isEmpty)
                || choices.stream().map(ItemStack::getItem).distinct().count() != 3) {
            throw new IllegalArgumentException("Invalid return cache");
        }
        haul = haul.stream().map(ItemStack::copy).toList();
        choices = choices.stream().map(ItemStack::copy).toList();
    }

    List<ItemStack> bundle(int choice) {
        var bundle = new ArrayList<>(haul);
        bundle.add(choices.get(choice));
        return bundle;
    }

    static ReturnCache from(ExpeditionRun run, ServerLevel level, long elapsed, boolean fieldFind) {
        String dimension = run.definition().dimension();
        var pools = ReclaimingOffers.forDimension(dimension, level);
        boolean hard = run.options().difficulty() == Difficulty.HARD || run.options().difficulty() == Difficulty.BRUTAL;
        boolean quiet = FrontierProgression.isAftergrowth(run.definition());
        var commissionId = run.commissionId();
        boolean commission = commissionId.filter(run.completedObjectiveIds()::contains).isPresent();
        long window = run.definition().bonusTimeTicks() * (long) (run.options().bonusTimePercent() == 0
                ? 100 : run.options().bonusTimePercent()) / 100;
        boolean bonus = window > 0 && elapsed >= 0 && elapsed <= window
                || run.definition().objectives().stream().anyMatch(o -> !o.required()
                && !o.id().equals("practice_complete") && run.completedObjectiveIds().contains(o.id())
                && commissionId.filter(o.id()::equals).isEmpty());
        var haul = new ArrayList<ItemStack>();
        var knot = new ItemStack(ModItems.RETURN_KNOT.get(), (hard && !quiet ? 2 : 1) + (commission ? 1 : 0));
        ReturnKnotItem.bind(knot, dimension);
        haul.add(knot);
        haul.addAll(supplies(dimension, pools.getFirst(), level));
        if (bonus) haul.addAll(supplies(dimension, pools.getFirst(), level));
        if (fieldFind) haul.add(ReclaimingOffers.cacheReward(dimension, level));
        var choices = new ArrayList<ItemStack>();
        int[] tiers = {0, 1, hard && !quiet ? 2 : 1};
        for (int tier : tiers) {
            var stack = pools.get(tier).drawExcept(level.random, choices);
            if (stack.isEmpty()) {
                stack = List.of(new ItemStack(ModItems.DORMANT_FLAKE.get(), 4),
                                new ItemStack(Items.COPPER_INGOT, 4), new ItemStack(Items.IRON_INGOT, 2))
                        .stream().filter(candidate -> choices.stream().noneMatch(chosen -> chosen.is(candidate.getItem())))
                        .findFirst().orElseThrow();
            }
            choices.add(stack);
        }
        return new ReturnCache(run.runId(), run.definition().displayName(), dimension, haul, choices);
    }

    private static List<ItemStack> supplies(String dimension, ReclaimingOffers.Offer pool, ServerLevel level) {
        return switch (dimension) {
            case "minecraft:overworld" -> List.of(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.COPPER_INGOT, 4));
            case "minecraft:the_nether" -> List.of(new ItemStack(Items.QUARTZ, 6), new ItemStack(Items.GOLD_INGOT, 2));
            case "minecraft:the_end" -> List.of(new ItemStack(Items.POPPED_CHORUS_FRUIT, 4), new ItemStack(Items.ENDER_PEARL, 2));
            default -> List.of(pool.draw(level.random));
        };
    }

    CompoundTag save(HolderLookup.Provider lookup) {
        var tag = new CompoundTag();
        tag.putUUID("Run", run);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension);
        tag.put("Haul", saveStacks(haul, lookup));
        tag.put("Choices", saveStacks(choices, lookup));
        return tag;
    }

    static ReturnCache load(CompoundTag tag, HolderLookup.Provider lookup) {
        return new ReturnCache(tag.getUUID("Run"), tag.getString("Name"), tag.getString("Dimension"),
                readStacks(tag.getList("Haul", Tag.TAG_COMPOUND), lookup),
                readStacks(tag.getList("Choices", Tag.TAG_COMPOUND), lookup));
    }

    private static ListTag saveStacks(List<ItemStack> stacks, HolderLookup.Provider lookup) {
        var list = new ListTag();
        for (var stack : stacks) list.add(stack.save(lookup));
        return list;
    }

    private static List<ItemStack> readStacks(ListTag list, HolderLookup.Provider lookup) {
        return list.stream().map(tag -> ItemStack.parse(lookup, tag).orElseThrow()).toList();
    }
}
