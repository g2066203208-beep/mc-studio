package dev.reme.worldseam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Ordered multi-dimension editor. Uses actual world-creation datapack dimension
 * registry so modded LevelStems are discoverable, not hard-coded vanilla IDs.
 * Blank top/bottom means the loaded dimension's native build boundary.
 * Changes are applied only when the actual Create World button is pressed.
 */
public final class SeamStackScreen extends Screen {
    private final CreateWorldScreen parent;
    private final List<SeamStack.Entry> entries=new ArrayList<>();
    private final List<String> candidates;
    private int selected;
    private int candidate;
    private boolean loop;
    private EditBox bottom;
    private EditBox top;
    private Button up;
    private Button down;
    private String error="";

    public SeamStackScreen(CreateWorldScreen parent,SeamStack existing) {
        super(Component.literal("WorldSeam Dimension Stack"));
        this.parent=parent;
        this.entries.addAll(existing.entries());
        this.loop=existing.loop();
        var found=new HashSet<String>();
        // LevelStem keys from the world's actual loaded datapack registry.
        // This includes registered dimensions from compatible mods/datapacks.
        parent.getUiState().getSettings().datapackDimensions().keySet()
            .forEach(key -> found.add(key.toString()));
        found.add("minecraft:overworld");
        found.add("minecraft:the_nether");
        found.add("minecraft:the_end");
        var ordered=new ArrayList<>(found);
        ordered.sort(String::compareTo);
        this.candidates=List.copyOf(ordered);
    }

    @Override protected void init() {
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_CREATE_STACK_UI_OPEN: {} available dimensions, {} configured layers",
            candidates.size(),entries.size());
        int c=width/2, y=Math.max(18,height/2-108);
        addRenderableWidget(Button.builder(Component.literal("Prev"),
            b -> moveSelection(-1)).bounds(c-145,y,65,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"),
            b -> moveSelection(1)).bounds(c+80,y,65,20).build());
        up=addRenderableWidget(Button.builder(Component.literal("Move UP"),
            b -> reorder(-1)).bounds(c-145,y+24,90,20).build());
        down=addRenderableWidget(Button.builder(Component.literal("Move DOWN"),
            b -> reorder(1)).bounds(c+55,y+24,90,20).build());

        bottom=new EditBox(font,c-139,y+68,125,20,
            Component.literal("Bottom seam Y"));
        top=new EditBox(font,c+14,y+68,125,20,
            Component.literal("Top seam Y"));
        bottom.setMaxLength(9);
        top.setMaxLength(9);
        addRenderableWidget(bottom);
        addRenderableWidget(top);
        loadFields();

        addRenderableWidget(Button.builder(Component.literal("Toggle UP"),
            b -> changeDirection(true)).bounds(c-145,y+91,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("Toggle DOWN"),
            b -> changeDirection(false)).bounds(c+55,y+91,90,20).build());

        addRenderableWidget(Button.builder(Component.literal("< Dim"),
            b -> browse(-1)).bounds(c-145,y+117,67,20).build());
        addRenderableWidget(Button.builder(Component.literal("Dim >"),
            b -> browse(1)).bounds(c+78,y+117,67,20).build());
        addRenderableWidget(Button.builder(Component.literal("Add"),
            b -> addDimension()).bounds(c-70,y+141,64,20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"),
            b -> removeDimension()).bounds(c+6,y+141,72,20).build());
        addRenderableWidget(Button.builder(Component.literal("Loop: "+(loop?"ON":"OFF")),
            b -> {
                loop=!loop;
                b.setMessage(Component.literal("Loop: "+(loop?"ON":"OFF")));
            }).bounds(c-145,y+166,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save stack"),
            b -> finish()).bounds(c+48,y+166,97,20).build());
        updateControls();
    }

    private void updateControls() {
        if(up!=null)up.active=selected>0;
        if(down!=null)down.active=selected<entries.size()-1;
    }

    private void loadFields() {
        if(entries.isEmpty())return;
        SeamStack.Entry e=entries.get(selected);
        bottom.setValue(e.bottomY()==null?"":e.bottomY().toString());
        top.setValue(e.topY()==null?"":e.topY().toString());
    }

    private Integer parseField(String value) {
        if(value.trim().isEmpty())return null;
        return Integer.valueOf(value.trim());
    }

    private boolean saveFields() {
        try {
            var old=entries.get(selected);
            entries.set(selected,new SeamStack.Entry(old.id(),
                parseField(bottom.getValue()),parseField(top.getValue()),
                old.connectsUp(),old.connectsDown()));
            error="";
            return true;
        } catch (IllegalArgumentException ex) {
            error="Invalid height: "+ex.getMessage();
            return false;
        }
    }

    private void moveSelection(int delta) {
        if(!saveFields())return;
        selected=Math.floorMod(selected+delta,entries.size());
        loadFields();
        updateControls();
    }

    private void reorder(int direction) {
        if(!saveFields())return;
        int next=selected+direction;
        if(next<0||next>=entries.size())return;
        var item=entries.remove(selected);
        entries.add(next,item);
        selected=next;
        updateControls();
    }

    private void changeDirection(boolean upwards) {
        if(!saveFields())return;
        var e=entries.get(selected);
        entries.set(selected,new SeamStack.Entry(e.id(),e.bottomY(),e.topY(),
            upwards?!e.connectsUp():e.connectsUp(),
            upwards?e.connectsDown():!e.connectsDown()));
    }

    private void browse(int delta) {
        if(candidates.isEmpty())return;
        candidate=Math.floorMod(candidate+delta,candidates.size());
    }

    private void addDimension() {
        if(!saveFields())return;
        String id=candidates.get(candidate);
        if(entries.stream().anyMatch(e->e.id().equals(id))){
            error="This dimension is already in stack";return;
        }
        if(entries.size()>=SeamStack.MAX_ENTRIES){
            error="Maximum "+SeamStack.MAX_ENTRIES+" dimensions";return;
        }
        entries.add(new SeamStack.Entry(id,null,null,true,true));
        selected=entries.size()-1;
        loadFields();
        updateControls();
        error="";
    }

    private void removeDimension() {
        if(entries.size()==1){error="At least one dimension is required";return;}
        entries.remove(selected);
        selected=Math.min(selected,entries.size()-1);
        loadFields();
        updateControls();
        error="";
    }

    private void finish() {
        if(!saveFields())return;
        try {
            SeamStack plan=new SeamStack(List.copyOf(entries),loop);
            SeamWorldCreation.save(parent,plan);
            Minecraft.getInstance().setScreen(parent);
        } catch(IllegalArgumentException ex) {
            error=ex.getMessage();
        }
    }

    @Override public void onClose() {
        // Cancel does not modify creation preset.
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        renderBackground(g,mouseX,mouseY,delta);
        super.render(g,mouseX,mouseY,delta);
        int c=width/2,y=Math.max(40,height/2-99);
        g.drawCenteredString(font,"WORLDSEAM  /  TOP  ->  BOTTOM",c,10,0xE6D7FF);
        if(!entries.isEmpty()){
            var e=entries.get(selected);
            g.drawCenteredString(font,
                "Layer "+(selected+1)+"/"+entries.size()+": "+e.id(),
                c,y-15,0xFFFFFF);
            g.drawCenteredString(font,
                "Connect UP: "+e.connectsUp()+"  |  DOWN: "+e.connectsDown(),
                c,y+51,0xAADDFF);
        }
        g.drawString(font,"Bottom Y (blank=default)",c-142,y+58,0xCCCCCC);
        g.drawString(font,"Top Y (blank=default)",c+13,y+58,0xCCCCCC);
        g.drawCenteredString(font,"Available dimension: "+candidates.get(candidate),
            c,y+106,0xAEE9A0);
        g.drawCenteredString(font,
            "Mods/datapacks: dimensions must exist in this world's registry",
            c,Math.min(height-12,y+194),0x999999);
        if(!error.isEmpty())g.drawCenteredString(font,error,c,Math.min(height-1,y+207),0xFF7777);
    }
}
