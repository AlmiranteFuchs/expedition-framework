package com.expeditions.client;

import com.expeditions.item.FieldAssayKitItem;
import com.expeditions.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** The kit reads a sealed sample; it never guesses an unsynchronized live infection value. */
public final class AssayScreen extends Screen {
    private final InteractionHand hand;
    private int page;
    private int pageCount = 1;
    public AssayScreen(InteractionHand hand) { super(Component.literal("Field assay")); this.hand = hand; }
    @Override public void render(GuiGraphics gui, int mx, int my, float tick) {
        if (minecraft.player == null || minecraft.level == null) { onClose(); return; }
        int w = Math.min(280, width - 16), h = Math.min(190, height - 16), x = (width-w)/2, y = (height-h)/2;
        gui.fill(0, 0, width, height, 0x99101612);
        gui.fill(x-2,y-2,x+w+2,y+h+2,0xff171c16);
        gui.fill(x,y,x+w,y+h,0xffaa9062);
        gui.fill(x+3,y+3,x+w-3,y+h-3,0xff283529);
        gui.drawString(font,"FIELD ASSAY",x+14,y+13,0xffe0d3ac,false);
        gui.renderFakeItem(new net.minecraft.world.item.ItemStack(ModItems.FIELD_ASSAY_KIT.get()),x+w-31,y+9);
        var record = FieldAssayKitItem.record(minecraft.player.getItemInHand(hand));
        String body;
        if (record.isEmpty()) {
            body = "No sample sealed.\n\nSneak-use this kit to draw blood. It costs one heart and refuses a lethal sample.\n\nUse normally to inspect the last result.";
        } else {
            String stage = switch(record.getInt("Stage")) { case 1 -> "Notice"; case 2 -> "Grip"; case 3 -> "Takeover"; default -> "Unmarked"; };
            long age = Math.max(0, minecraft.level.getGameTime()-record.getLong("Time"))/20;
            body = "Patient: " + record.getString("Patient") + "\nInfection: " + record.getInt("Percent") + "%\nStage: " + stage
                    + "\nSample age: " + age + "s\n\nThis is the sealed sample's result. Sneak-use to take a fresh sample.";
        }
        var lines = font.split(Component.literal(body),w-30);
        int perPage = Math.max(2,(h-65)/10);
        pageCount = Math.max(1,(lines.size()+perPage-1)/perPage);
        page = Math.clamp(page,0,pageCount-1);
        for (int i=page*perPage;i<Math.min(lines.size(),(page+1)*perPage);i++) {
            gui.drawString(font,lines.get(i),x+15,y+39+(i%perPage)*10,0xffd7d1b4,false);
        }
        gui.drawString(font,"Esc · close",x+14,y+h-16,0xffa79c7e,false);
        gui.drawString(font,(page+1)+"/"+pageCount+"  < >",x+w-79,y+h-16,0xffa79c7e,false);
    }
    @Override public void tick() {
        if (minecraft.player == null || !minecraft.player.getItemInHand(hand).is(ModItems.FIELD_ASSAY_KIT.get())) onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (key==262 || key==263) { page=Math.clamp(page+(key==262?1:-1),0,pageCount-1);return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (vertical == 0) return false;
        page=Math.clamp(page+(vertical<0?1:-1),0,pageCount-1);return true;
    }
}
