package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
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
import java.util.Set;

/**
 * WorldSeam 创建世界 · 中文维度堆叠工作台。
 *
 * Left: visible TOP -> BOTTOM world layers with direct selection.
 * Right: selected layer's exact physical seam coordinates and travel rules.
 * All geometry/layout is computed from the actual GUI-scaled resolution.
 *
 * This edits connection planes, NOT DimensionType min_y/height.
 */
public final class SeamStackScreen extends Screen {
    private static final int NAVY=0xEE101A2A;
    private static final int PANEL=0xE8223042;
    private static final int OUTLINE=0xFF52687D;
    private static final int ACCENT=0xFF73C6BD;
    private static final int WHITE=0xFFF0F5F9;
    private static final int MUTED=0xFFAFBECA;

    private final CreateWorldScreen parent;
    private final List<SeamStack.Entry> entries=new ArrayList<>();
    private final List<String> candidates;
    private int selected=0, scroll=0, candidate=0;
    private boolean loop;
    private EditBox bottom,top;
    private Button up,down,connectUp,connectDown;
    private int panelX,panelY,panelW,panelH,leftX,leftW,rightX,rightW;
    private int listY,rowH,rowsVisible;
    private boolean compact;
    private String error="";

    public SeamStackScreen(CreateWorldScreen parent,SeamStack saved) {
        super(Component.literal("WorldSeam · 维度堆叠"));
        this.parent=parent;
        this.entries.addAll(saved.entries());
        this.loop=saved.loop();
        Set<String> ids=new HashSet<>();
        parent.getUiState().getSettings().datapackDimensions().keySet()
            .forEach(key -> ids.add(key.toString()));
        ids.add("minecraft:overworld");
        ids.add("minecraft:the_nether");
        ids.add("minecraft:the_end");
        var available=new ArrayList<>(ids);
        available.sort(String::compareTo);
        this.candidates=List.copyOf(available);
        candidate=Math.max(0,candidates.indexOf("minecraft:overworld"));
    }

    private static Component text(String value) { return Component.literal(value); }
    private static String label(String dimension) {
        return switch(dimension) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> dimension.substring(dimension.indexOf(':')+1);
        };
    }

    private String truncate(String value,int pixels) {
        String text=value;
        while(text.length()>2 && font.width(text)>pixels)
            text=text.substring(0,text.length()-2)+"…";
        return text;
    }

    private static String endpoint(Integer value) {
        return value==null?"原生边界":value.toString();
    }

    private void layout() {
        panelW=Math.min(width-18,760);
        panelH=Math.min(height-14,416);
        panelX=(width-panelW)/2;
        panelY=(height-panelH)/2;
        leftX=panelX+14;
        leftW=(int)((panelW-42)*0.44);
        rightX=leftX+leftW+14;
        rightW=panelX+panelW-14-rightX;
        // Default MC GUI scale can make a 720px-tall window only ~240
        // GUI units high. Never lay out as though it were 720 units tall.
        compact=panelH<310;
        listY=panelY+(compact?38:61);
        rowH=compact?26:35;
        rowsVisible=compact?3:(panelH>=370?5:4);
    }

    private Button button(String name,int x,int y,int w,int h,Runnable action) {
        return addRenderableWidget(Button.builder(text(name),b->action.run())
            .bounds(x,y,Math.max(28,w),h).build());
    }

    @Override protected void init() {
        layout();
        LogUtils.getLogger().info(
            "WORLDSEAM_CREATE_STACK_UI_OPEN: {} available dimensions, {} layers, gui={}x{}, default/current top-to-bottom={}",
            candidates.size(),entries.size(),width,height,
            String.join(" > ",entries.stream().map(SeamStack.Entry::id).toList()));

        // Direct clickable world layer cards (only visible entries).
        scroll=Math.max(0,Math.min(scroll,Math.max(0,entries.size()-rowsVisible)));
        for(int row=0;row<rowsVisible;row++){
            int index=scroll+row;
            if(index>=entries.size())break;
            final int layer=index;
            button((index+1)+". "+label(entries.get(index).id()),
                leftX+6,listY+row*rowH,leftW-12,compact?18:21,()->select(layer));
        }

        int toolsY=listY+Math.min(rowsVisible,entries.size())*rowH+3;
        button("◀ 上页",leftX+6,toolsY,(leftW-18)/2,compact?17:20,()->page(-1));
        button("下页 ▶",leftX+12+(leftW-18)/2,toolsY,(leftW-18)/2,compact?17:20,()->page(1));
        int chooserY=toolsY+(compact?24:40);
        button("◀",leftX+6,chooserY,30,compact?17:20,()->browse(-1));
        button("▶",leftX+leftW-36,chooserY,30,compact?17:20,()->browse(1));
        button("＋ 添加维度",leftX+6,chooserY+(compact?21:25),
            leftW-74,compact?18:21,this::addDimension);
        button("删除",leftX+leftW-62,chooserY+(compact?21:25),
            56,compact?18:21,this::removeDimension);

        int editX=rightX+12, editW=rightW-24;
        bottom=new EditBox(font,editX,panelY+(compact?81:97),
            editW,compact?18:21,text("下边界 Y"));
        top=new EditBox(font,editX,panelY+(compact?120:150),
            editW,compact?18:21,text("上边界 Y"));
        bottom.setMaxLength(9);
        top.setMaxLength(9);
        addRenderableWidget(bottom);
        addRenderableWidget(top);
        loadFields();

        int half=(editW-8)/2;
        connectUp=button("上方：允许",editX,panelY+(compact?145:207),
            half,compact?18:21,()->direction(true));
        connectDown=button("下方：允许",editX+half+8,panelY+(compact?145:207),
            half,compact?18:21,()->direction(false));
        up=button("↑ 上移一层",editX,panelY+(compact?169:246),
            half,compact?18:21,()->reorder(-1));
        down=button("↓ 下移一层",editX+half+8,panelY+(compact?169:246),
            half,compact?18:21,()->reorder(1));
        if(!compact)
            button("恢复默认顺序",editX,panelY+278,editW,20,this::restoreDefault);

        int footer=panelY+panelH-(compact?25:36);
        button("取消",panelX+14,footer,compact?60:86,compact?19:24,this::onClose);
        button("循环："+(loop?"开":"关"),panelX+(compact?79:109),footer,
            compact?73:100,compact?19:24,()->{
            if(!saveFields())return;
            loop=!loop;
            rebuild();
        });
        button("保存堆叠",panelX+panelW-(compact?122:180),footer,
            compact?108:166,compact?19:24,this::finish);
        updateControls();
    }

    private void rebuild() { Minecraft.getInstance().setScreen(this); }
    private void keepVisible() {
        if(selected<scroll)scroll=selected;
        if(selected>=scroll+rowsVisible)scroll=selected-rowsVisible+1;
    }
    private void updateControls() {
        if(up!=null)up.active=selected>0;
        if(down!=null)down.active=selected<entries.size()-1;
        var e=entries.get(selected);
        if(connectUp!=null)connectUp.setMessage(text("向上："+(e.connectsUp()?"连通":"断开")));
        if(connectDown!=null)connectDown.setMessage(text("向下："+(e.connectsDown()?"连通":"断开")));
    }
    private void loadFields() {
        var e=entries.get(selected);
        bottom.setValue(e.bottomY()==null?"":e.bottomY().toString());
        top.setValue(e.topY()==null?"":e.topY().toString());
    }
    private static Integer parseHeight(String value) {
        return value.isBlank()?null:Integer.valueOf(value.trim());
    }
    private boolean saveFields() {
        if(bottom==null||top==null)return true;
        try {
            var old=entries.get(selected);
            entries.set(selected,new SeamStack.Entry(
                old.id(),parseHeight(bottom.getValue()),parseHeight(top.getValue()),
                old.connectsUp(),old.connectsDown()));
            error="";
            return true;
        } catch (IllegalArgumentException ex) {
            error="高度输入无效："+ex.getMessage();
            return false;
        }
    }
    private void select(int index) {
        if(index==selected)return;
        if(!saveFields())return;
        selected=index;
        rebuild();
    }
    private void page(int dir) {
        if(!saveFields())return;
        scroll=Math.max(0,Math.min(scroll+dir*rowsVisible,
            Math.max(0,entries.size()-rowsVisible)));
        rebuild();
    }
    private void browse(int offset) {
        if(!saveFields())return;
        candidate=Math.floorMod(candidate+offset,candidates.size());
    }
    private void reorder(int offset) {
        if(!saveFields())return;
        int next=selected+offset;
        if(next<0||next>=entries.size())return;
        var e=entries.remove(selected);
        entries.add(next,e);
        selected=next;
        keepVisible();
        rebuild();
    }
    private void direction(boolean upDirection) {
        if(!saveFields())return;
        var e=entries.get(selected);
        entries.set(selected,new SeamStack.Entry(e.id(),e.bottomY(),e.topY(),
            upDirection?!e.connectsUp():e.connectsUp(),
            upDirection?e.connectsDown():!e.connectsDown()));
        updateControls();
    }
    private void addDimension() {
        if(!saveFields())return;
        String id=candidates.get(candidate);
        if(entries.stream().anyMatch(x->x.id().equals(id))) {
            error="该维度已在堆叠中";return;
        }
        if(entries.size()>=SeamStack.MAX_ENTRIES) {
            error="最多支持 "+SeamStack.MAX_ENTRIES+" 层";return;
        }
        entries.add(new SeamStack.Entry(id,null,null,true,true));
        selected=entries.size()-1;
        keepVisible();
        rebuild();
    }
    private void removeDimension() {
        if(entries.size()==1) {error="至少保留一个维度";return;}
        entries.remove(selected);
        selected=Math.min(selected,entries.size()-1);
        keepVisible();
        rebuild();
    }
    private void restoreDefault() {
        entries.clear();
        entries.addAll(SeamStack.defaults().entries());
        loop=false;
        scroll=selected=0;
        rebuild();
    }
    private void finish() {
        if(!saveFields())return;
        try {
            SeamStack next=new SeamStack(List.copyOf(entries),loop);
            SeamWorldCreation.save(parent,next);
            LogUtils.getLogger().info(
                "WORLDSEAM_STACK_EDITOR_SAVED: layers={}, order={}",
                next.entries().size(),next.entries().stream().map(SeamStack.Entry::id).toList());
            Minecraft.getInstance().setScreen(parent);
        } catch (IllegalArgumentException ex) {
            error="无法保存："+ex.getMessage();
        }
    }
    @Override public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        renderBackground(g,mouseX,mouseY,delta);
        layout();
        g.fill(panelX,panelY,panelX+panelW,panelY+panelH,NAVY);
        g.fill(panelX+7,panelY+48,panelX+panelW-7,panelY+panelH-43,PANEL);
        g.fill(rightX-8,panelY+56,rightX-7,panelY+panelH-46,OUTLINE);
        g.drawString(font,"维度堆叠",panelX+12,panelY+9,WHITE,true);
        g.drawString(font,compact?"从上到下排列":"从上到下排列 · 点击左侧卡片编辑连接",
            panelX+(compact?85:114),panelY+10,MUTED);
        g.drawString(font,"世界层级",leftX+6,panelY+(compact?28:46),ACCENT);
        g.drawString(font,"连接高度与方向",rightX+12,
            panelY+(compact?28:46),ACCENT);

        for(int i=0;i<rowsVisible;i++){
            int index=scroll+i;
            if(index>=entries.size())break;
            int y=listY+i*rowH;
            var e=entries.get(index);
            if(index==selected) {
                g.fill(leftX+3,y-2,leftX+leftW-3,y+rowH-2,0x663E9B93);
                g.fill(leftX+3,y-2,leftX+5,y+rowH-2,ACCENT);
            }
            String details=e.id()+"  ↑"+endpoint(e.topY())+" ↓"+endpoint(e.bottomY());
            g.drawString(font,truncate(details,leftW-18),
                leftX+9,y+(compact?19:24),
                index==selected?0xFFF5D49E:MUTED,false);
        }

        var active=entries.get(selected);
        g.drawString(font,label(active.id()),rightX+12,
            panelY+(compact?38:63),WHITE,true);
        g.drawString(font,truncate(active.id(),rightW-25),
            rightX+12,panelY+(compact?50:76),MUTED,false);
        g.drawString(font,compact?"下边界 Y（留空=默认）":
            "下连接面 Y（留空使用原生下界）",
            rightX+12,panelY+(compact?69:87),MUTED);
        g.drawString(font,compact?"上边界 Y（留空=默认）":
            "上连接面 Y（留空使用原生上界）",
            rightX+12,panelY+(compact?108:140),MUTED);
        int toolsTop=listY+Math.min(rowsVisible,entries.size())*rowH+3;
        g.drawString(font,"添加维度",leftX+6,
            toolsTop+(compact?18:29),MUTED);
        g.drawCenteredString(font,
            truncate(label(candidates.get(candidate)),leftW-78),
            leftX+leftW/2,toolsTop+(compact?29:46),WHITE);
        if(!compact) {
            g.drawString(font,"连通开关",rightX+12,panelY+190,MUTED);
            g.drawString(font,"层级排序",rightX+12,panelY+233,MUTED);
        }
        if(panelH>=390) {
            g.drawString(font,"仅修改连接面，不修改世界生成高度",
                rightX+12,panelY+308,0xFFE1B680);
            g.drawString(font,"不存在的维度会被服务器拒绝",
                rightX+12,panelY+324,MUTED);
        }
        if(loop && !compact)g.drawString(font,"循环连接：已开启",panelX+228,
            panelY+panelH-29,ACCENT);
        if(!error.isBlank())
            g.drawCenteredString(font,
                truncate(error,panelW-30),
                width/2,panelY+panelH-(compact?40:49),0xFFFF8989);
        super.render(g,mouseX,mouseY,delta);
    }
}
