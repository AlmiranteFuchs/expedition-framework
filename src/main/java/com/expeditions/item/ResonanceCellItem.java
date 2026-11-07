package com.expeditions.item;

import com.expeditions.core.FieldEquipment;
import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.List;

/** Component-backed portable FE. Every transfer charges the actual held stack. */
public final class ResonanceCellItem extends Item {
    public ResonanceCellItem(Properties p) { super(p); }
    public static int energy(ItemStack stack) { return FieldEquipment.stored(stack.getOrDefault(ModDataComponents.RESONANCE.get(), 0)); }
    public static boolean spend(ItemStack stack, int amount) {
        if (!stack.is(ModItems.RESONANCE_CELL.get()) || amount < 0 || energy(stack) < amount) return false;
        stack.set(ModDataComponents.RESONANCE.get(), energy(stack) - amount); return true;
    }
    public static boolean spendCarried(Player player, int amount) {
        // One cell must cover a reading; no partial payment across several depleted cells.
        for (var stack : player.getInventory().items) if (spend(stack, amount)) return true;
        return spend(player.getOffhandItem(), amount);
    }
    public static IEnergyStorage storage(ItemStack stack) { return new IEnergyStorage() {
        public int receiveEnergy(int amount, boolean simulate) {
            int moved=FieldEquipment.transfer(energy(stack),amount,true);
            if (!simulate && moved>0) stack.set(ModDataComponents.RESONANCE.get(),energy(stack)+moved); return moved;
        }
        public int extractEnergy(int amount, boolean simulate) {
            int moved=FieldEquipment.transfer(energy(stack),amount,false);
            if (!simulate && moved>0) stack.set(ModDataComponents.RESONANCE.get(),energy(stack)-moved); return moved;
        }
        public int getEnergyStored() { return energy(stack); }
        public int getMaxEnergyStored() { return FieldEquipment.CELL_CAPACITY; }
        public boolean canExtract() { return true; }
        public boolean canReceive() { return true; }
    }; }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM,(stack,context)->storage(stack),ModItems.RESONANCE_CELL.get());
    }
    public static boolean transferToBlock(UseOnContext context) {
        var level=context.getLevel();var player=context.getPlayer();if(player==null)return false;
        if (level.getBlockState(context.getClickedPos()).getBlock() instanceof com.expeditions.block.ResonanceMachineBlock) return false;
        var other=level.getCapability(Capabilities.EnergyStorage.BLOCK,context.getClickedPos(),context.getClickedFace());
        if(other==null)return false;
        if(!level.isClientSide) {
            var cell=storage(context.getItemInHand());boolean charging=player.isShiftKeyDown();
            int offered=charging?other.extractEnergy(cell.receiveEnergy(FieldEquipment.TRANSFER,true),true):cell.extractEnergy(other.receiveEnergy(FieldEquipment.TRANSFER,true),true);
            int moved=charging?cell.receiveEnergy(other.extractEnergy(offered,false),false):other.receiveEnergy(cell.extractEnergy(offered,false),false);
            if(moved>0)player.playNotifySound(com.expeditions.registry.ModSounds.CELL_INSERT.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS,.7f,1.0f);
            player.displayClientMessage(Component.translatable("equipment.expeditions.transfer",moved,energy(context.getItemInHand()),FieldEquipment.CELL_CAPACITY),true);
            player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        }
        return true;
    }
    @Override public InteractionResult useOn(UseOnContext context) { return transferToBlock(context)?InteractionResult.sidedSuccess(context.getLevel().isClientSide):InteractionResult.PASS; }
    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13f*energy(s)/FieldEquipment.CELL_CAPACITY); }
    @Override public int getBarColor(ItemStack s) { return 0xd6a16a; }
    @Override public void appendHoverText(ItemStack s,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("equipment.expeditions.cell_charge",energy(s),FieldEquipment.CELL_CAPACITY).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("equipment.expeditions.cell_hint").withStyle(ChatFormatting.GRAY));
    }
}
