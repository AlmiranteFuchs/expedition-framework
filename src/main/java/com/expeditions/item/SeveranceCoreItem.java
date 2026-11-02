package com.expeditions.item;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.util.List;
public final class SeveranceCoreItem extends Item {
    public SeveranceCoreItem(Properties p){super(p);}
    public static String source(ItemStack stack){
        if(!stack.is(ModItems.SEVERANCE_CORE.get()))return "";
        var data=stack.get(DataComponents.CUSTOM_DATA);return data==null?"":data.copyTag().getString("severed_dimension");
    }
    public static boolean valid(ServerLevel level,ItemStack stack){
        String dim=source(stack);if(dim.isBlank())return false;var data=ExpeditionManager.get(level.getServer()).savedData();
        return !dim.isBlank() && data.isDimensionCleared(dim) && data.hasGuardianLiberation(dim);
    }
    public static void bind(ItemStack stack,String dimension){
        var old=stack.get(DataComponents.CUSTOM_DATA);var tag=old==null?new CompoundTag():old.copyTag();tag.putString("severed_dimension",dimension);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    @Override public Component getName(ItemStack stack){return Component.translatable(source(stack).isBlank()?"item.expeditions.severance_core":"item.expeditions.severance_core.bound");}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        String source=source(stack);lines.add(Component.translatable(source.isBlank()?"tooltip.expeditions.core_unbound":"tooltip.expeditions.core_bound",source));
    }
}
