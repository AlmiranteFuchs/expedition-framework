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
        int progress=menu.dynamo?(menu.progress()>0?100-menu.progress():0):menu.progress();int length=22*progress/(menu.dynamo?100:200);
        g.fill(leftPos+82,topPos+41,leftPos+82+length,topPos+44,0xffd39a45);
        String label=menu.breaching()?"BREACH OPEN":menu.dynamo?"Flake -> 4000 FE":"4000 FE / essence";
        g.drawString(font,label,leftPos+32,topPos+60,0xff514737,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(x>=leftPos+9&&x<leftPos+23&&y>=topPos+18&&y<topPos+69)g.renderTooltip(font,Component.literal(menu.energy()+" / 16000 FE"),x,y);
    }
}
