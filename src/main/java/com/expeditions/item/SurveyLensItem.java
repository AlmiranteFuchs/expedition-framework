package com.expeditions.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

/** A recovered-heart instrument carried with the issued expedition compass. */
public final class SurveyLensItem extends Item {
    public SurveyLensItem(Properties p){super(p);}
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("equipment.expeditions.lens_hint").withStyle(ChatFormatting.GRAY));
    }
}
