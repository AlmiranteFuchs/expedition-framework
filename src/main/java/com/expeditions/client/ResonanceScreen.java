package com.expeditions.client;
import com.expeditions.server.ResonanceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
public final class ResonanceScreen extends AbstractContainerScreen<ResonanceMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("expeditions:textures/gui/resonance.png");
    public ResonanceScreen(ResonanceMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166;titleLabelY=6;inventoryLabelY=72;}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight,176,166);
        int fill=46*menu.energy()/16000;g.fill(leftPos+12,topPos+66-fill,leftPos+18,topPos+66,0xffd39a45);
        if(menu.dynamo)g.fill(leftPos+78,topPos+32,leftPos+134,topPos+53,0xffd1c09c);
        int progress=menu.dynamo && menu.sourceKind()==1?menu.progress():menu.dynamo?0:menu.progress();int length=22*progress/(menu.dynamo?600:200);
        g.fill(leftPos+82,topPos+41,leftPos+82+length,topPos+44,0xffd39a45);
        String label=menu.breaching()?"BREACH OPEN":menu.dynamo?menu.generationRate()+" Resonance/t":"4000 R / essence";
        g.drawString(font,label,leftPos+28,topPos+60,0xff514737,false);
        if(menu.dynamo && menu.sourceKind()==0 && !menu.getSlot(0).hasItem())
            g.renderItem(new net.minecraft.world.item.ItemStack(com.expeditions.registry.ModItems.DORMANT_KNOT.get()),leftPos+53,topPos+35);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(x>=leftPos+9&&x<leftPos+23&&y>=topPos+18&&y<topPos+69)g.renderTooltip(font,Component.literal(menu.energy()+" / 16000 Resonance (FE)"),x,y);
    }
}
