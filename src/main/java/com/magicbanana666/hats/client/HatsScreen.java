package com.magicbanana666.hats.client;

import com.magicbanana666.bananautil.client.render.GuiEntityRenderer;
import com.magicbanana666.hats.common.hat.HatHandler;
import com.magicbanana666.hats.common.hat.HatInfo;
import com.magicbanana666.hats.common.hat.HatPart;
import com.magicbanana666.hats.common.hat.HatResources;
import com.magicbanana666.hats.common.network.SetHatPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The hats menu (H): pick one of your hats, toggle its accessories, colour it, and see it on yourself.
 * A simpler take on the original's "Workspace" GUI, built from vanilla widgets.
 */
public class HatsScreen extends Screen
{
    private final LocalPlayer player;
    private final List<HatPart> source;
    private final Map<String, HatPart> customised = new LinkedHashMap<>();
    @Nullable
    private HatPart selected;
    private boolean wearSelected;
    /** Did the player pick another hat or take theirs off? If not, the worn hat is left as it is (it may not be in the unlocked list, EG set by a command). */
    private boolean wornChanged;

    private HatList hatList;
    private AccessoryList accessoryList;
    private EditBox search;
    private Checkbox onlyUnlocked;
    private final List<AbstractSliderButton> sliders = new ArrayList<>();
    private Button colourButton;
    private Button favouriteButton;
    private Button glintButton;
    private boolean showColours;
    private float previewYaw = -25F;

    private int previewX0, previewY0, previewX1, previewY1;

    public HatsScreen(LocalPlayer player)
    {
        super(Component.translatable("hats.gui.selection.title"));
        this.player = player;
        this.source = HatHandler.getHatSource(player);
        HatPart worn = HatHandler.getHat(player);
        if(worn.isAHat() && worn.count > 0)
        {
            for(HatPart part : source)
            {
                if(part.name.equals(worn.name))
                {
                    part.copyPersonalisation(worn);
                    selected = part;
                    wearSelected = true;
                }
            }
        }
        HatsClient.previewEntity = player;
        HatsClient.previewHat = wearSelected ? selected : null;
    }

    private boolean isUnlocked(HatPart part)
    {
        return part.count > 0 || !HatHandler.useInventory(player);
    }

    @Override
    protected void init()
    {
        int listWidth = Math.max(110, (int)(width * 0.30F));
        int rightWidth = Math.max(100, (int)(width * 0.24F));
        int top = 22;
        int bottom = height - 30;

        search = new EditBox(font, 8, top, listWidth - 4, 16, search, Component.translatable("hats.gui.search"));
        search.setHint(Component.translatable("hats.gui.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> refreshList());
        addRenderableWidget(search);

        hatList = new HatList(minecraft, listWidth, bottom - top - 40, top + 20, 14);
        hatList.setX(6);
        addRenderableWidget(hatList);

        onlyUnlocked = Checkbox.builder(Component.translatable("hats.gui.onlyUnlocked"), font)
                .pos(8, bottom - 18)
                .selected(onlyUnlocked == null ? HatHandler.useInventory(player) : onlyUnlocked.selected())
                .onValueChange((box, value) -> refreshList())
                .build();
        addRenderableWidget(onlyUnlocked);

        int rightX = width - rightWidth - 6;
        accessoryList = new AccessoryList(minecraft, rightWidth, bottom - top - 44, top + 42, 14);
        accessoryList.setX(rightX);
        addRenderableWidget(accessoryList);

        colourButton = addRenderableWidget(Button.builder(Component.translatable("hats.gui.button.colourise"), b -> {
            showColours = !showColours;
            updateWidgets();
        }).bounds(rightX, top, rightWidth, 18).build());

        String[] names = {"hats.gui.colour.red", "hats.gui.colour.green", "hats.gui.colour.blue", "hats.gui.colour.alpha", "hats.gui.colour.hue", "hats.gui.colour.saturation", "hats.gui.colour.brightness"};
        sliders.clear();
        for(int i = 0; i < names.length; i++)
        {
            ColourSlider slider = new ColourSlider(rightX, top + 42 + i * 21, rightWidth, 20, names[i], i);
            sliders.add(slider);
            addRenderableWidget(slider);
        }

        int buttonsY = height - 24;
        int previewLeft = listWidth + 14;
        int previewRight = rightX - 8;
        int bw = Math.max(40, (previewRight - previewLeft - 8) / 3);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(previewLeft, buttonsY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("hats.gui.button.removeHat"), b -> {
            wearSelected = false;
            wornChanged = true;
            HatsClient.previewHat = null;
            updateWidgets();
        }).bounds(previewLeft + bw + 4, buttonsY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("hats.gui.button.randomHat"), b -> randomHat())
                .tooltip(Tooltip.create(Component.translatable("hats.gui.button.randomHat.tooltip")))
                .bounds(previewLeft + (bw + 4) * 2, buttonsY, bw, 20).build());

        //customisation buttons live in the right column
        int half = (rightWidth - 4) / 2;
        favouriteButton = addRenderableWidget(Button.builder(Component.translatable("hats.gui.button.favouriteHat"), b -> {
            if(selected != null)
            {
                selected.isFavourite = !selected.isFavourite;
                markCustomised();
                refreshList();
                updateWidgets();
            }
        }).bounds(rightX, top + 20, half, 18).build());
        glintButton = addRenderableWidget(Button.builder(Component.translatable("hats.gui.button.glint"), b -> {
            if(selected != null)
            {
                selected.enchanted = !selected.enchanted;
                markCustomised();
            }
        }).bounds(rightX + half + 4, top + 20, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("hats.gui.button.reset"), b -> {
            if(selected != null)
            {
                selected.colouriser = new float[] {0F, 0F, 0F, 0F};
                selected.hsbiser = new float[] {0F, 0F, 0F};
                selected.enchanted = false;
                markCustomised();
                updateWidgets();
            }
        }).bounds(rightX, buttonsY, rightWidth, 20).build());

        previewX0 = previewLeft;
        previewX1 = previewRight;
        previewY0 = top;
        previewY1 = buttonsY - 4;

        refreshList();
        updateWidgets();
    }

    private void refreshList()
    {
        String filter = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        boolean unlockedOnly = onlyUnlocked != null && onlyUnlocked.selected();
        List<HatPart> shown = new ArrayList<>();
        for(HatPart part : source)
        {
            if(unlockedOnly && !isUnlocked(part))
            {
                continue;
            }
            if(!filter.isEmpty() && !part.has(filter))
            {
                continue;
            }
            shown.add(part);
        }
        shown.sort(Comparator.<HatPart, Boolean>comparing(p -> !p.isFavourite).thenComparing(p -> !isUnlocked(p)).thenComparing(p -> p.name.toLowerCase(Locale.ROOT)));
        hatList.setHats(shown);
    }

    private void select(HatPart part)
    {
        selected = part;
        if(isUnlocked(part))
        {
            wearSelected = true;
            wornChanged = true;
            HatsClient.previewHat = part;
        }
        else
        {
            //locked hats can be looked at, not worn
            wearSelected = false;
            HatPart preview = part.createCopy();
            preview.count = 1;
            HatsClient.previewHat = preview;
        }
        updateWidgets();
    }

    private void randomHat()
    {
        List<HatPart> unlocked = source.stream().filter(this::isUnlocked).toList();
        if(unlocked.isEmpty())
        {
            return;
        }
        Random random = new Random();
        HatPart part = unlocked.get(random.nextInt(unlocked.size()));
        for(HatPart acc : part.hatParts)
        {
            acc.isShowing = isUnlocked(acc) && random.nextBoolean();
        }
        if(hasShiftDown())
        {
            part.hsbiser[0] = random.nextFloat();
        }
        select(part);
        markCustomised();
        hatList.selectPart(part);
    }

    private static boolean hasShiftDown()
    {
        return Minecraft.getInstance().hasShiftDown();
    }

    private void markCustomised()
    {
        if(selected != null)
        {
            customised.put(selected.name, selected);
        }
    }

    private void updateWidgets()
    {
        boolean hasHat = selected != null && isUnlocked(selected);
        accessoryList.visible = !showColours;
        accessoryList.setHat(selected);
        for(AbstractSliderButton slider : sliders)
        {
            slider.visible = showColours;
            slider.active = hasHat;
            ((ColourSlider)slider).refresh();
        }
        colourButton.setMessage(Component.translatable(showColours ? "hats.gui.button.personalise" : "hats.gui.button.colourise"));
        favouriteButton.active = hasHat;
        glintButton.active = hasHat;
        if(selected != null)
        {
            favouriteButton.setTooltip(Tooltip.create(Component.translatable(selected.isFavourite ? "hats.gui.unfavourite" : "hats.gui.button.favouriteHat")));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);

        int x0 = previewX0, x1 = previewX1, y0 = previewY0, y1 = previewY1;
        if(x1 - x0 > 20 && y1 - y0 > 20)
        {
            graphics.fill(x0, y0, x1, y1, 0x50000000);
            int top = y0 + 26;
            float size = Math.min((x1 - x0) / 1.4F, (y1 - top) / 2.7F);
            GuiEntityRenderer.render(graphics, player, x0, top, x1, y1, size, previewYaw, 5F, partialTick, 0.35F, null);

            Component name = selected != null ? describe(selected) : Component.translatable("hats.gui.noHat").withStyle(ChatFormatting.GRAY);
            graphics.centeredText(font, name, (x0 + x1) / 2, y0 + 4, 0xFFFFFFFF);
            if(selected != null)
            {
                HatInfo info = HatResources.get(selected);
                if(info != null)
                {
                    MutableComponent rarity = Component.translatable("hats.gui.tooltip.rarity", Component.literal(info.getRarity().name().toLowerCase(Locale.ROOT)).withStyle(info.getRarity().getColour()));
                    graphics.centeredText(font, rarity, (x0 + x1) / 2, y0 + 15, 0xFFAAAAAA);
                    if(info.project.author != null && !info.project.author.isEmpty())
                    {
                        graphics.centeredText(font, Component.translatable("hats.gui.tooltip.author", info.project.author), (x0 + x1) / 2, y1 - 11, 0xFFAAAAAA);
                    }
                }
                if(!isUnlocked(selected))
                {
                    graphics.centeredText(font, Component.translatable("hats.gui.tooltip.notUnlocked").withStyle(ChatFormatting.RED), (x0 + x1) / 2, y0 + 26, 0xFFFFFFFF);
                }
            }
        }
    }

    private Component describe(HatPart part)
    {
        HatInfo info = HatResources.get(part);
        MutableComponent name = info != null ? info.getDisplayName() : Component.literal(part.name);
        if(part.count > 1)
        {
            name.append(Component.literal(" x" + part.count).withStyle(ChatFormatting.GRAY));
        }
        return name;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy)
    {
        if(event.x() >= previewX0 && event.x() <= previewX1 && event.y() >= previewY0 && event.y() <= previewY1)
        {
            previewYaw = Mth.wrapDegrees(previewYaw + (float)dx * 2F);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void onClose()
    {
        HatPart worn = wearSelected && selected != null && isUnlocked(selected) ? selected : null;
        if(worn != null)
        {
            customised.put(worn.name, worn);
        }
        List<HatPart> changed = new ArrayList<>();
        for(HatPart part : customised.values())
        {
            changed.add(part.createCopy());
        }
        //an unchanged unlocked hat is still sent, so its colours / accessories are applied
        boolean changeWorn = wornChanged || worn != null;
        ClientPacketDistributor.sendToServer(new SetHatPayload(changeWorn, Optional.ofNullable(worn == null ? null : worn.createCopy()), changed));
        HatsClient.previewEntity = null;
        HatsClient.previewHat = null;
        super.onClose();
    }

    @Override
    public void removed()
    {
        HatsClient.previewEntity = null;
        HatsClient.previewHat = null;
        super.removed();
    }

    // ------------------------------------------------------------------ hat list

    private class HatList extends ObjectSelectionList<HatList.Entry>
    {
        HatList(Minecraft minecraft, int width, int height, int y, int itemHeight)
        {
            super(minecraft, width, height, y, itemHeight);
        }

        void setHats(List<HatPart> hats)
        {
            List<Entry> entries = new ArrayList<>();
            Entry toSelect = null;
            for(HatPart part : hats)
            {
                Entry entry = new Entry(part);
                entries.add(entry);
                if(part == selected)
                {
                    toSelect = entry;
                }
            }
            replaceEntries(entries);
            setSelected(toSelect);
        }

        void selectPart(HatPart part)
        {
            for(Entry entry : children())
            {
                if(entry.part == part)
                {
                    setSelected(entry);
                    scrollToEntry(entry);
                }
            }
        }

        @Override
        public int getRowWidth()
        {
            return width - 12;
        }

        private class Entry extends ObjectSelectionList.Entry<Entry>
        {
            final HatPart part;

            Entry(HatPart part)
            {
                this.part = part;
            }

            @Override
            public Component getNarration()
            {
                return Component.literal(part.name);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a)
            {
                boolean unlocked = isUnlocked(part);
                MutableComponent text = Component.empty();
                if(part.isFavourite)
                {
                    text.append(Component.literal("★ ").withStyle(ChatFormatting.YELLOW));
                }
                HatInfo info = HatResources.get(part);
                MutableComponent name = Component.literal(part.name);
                if(!unlocked)
                {
                    name.withStyle(ChatFormatting.DARK_GRAY);
                }
                else if(info != null)
                {
                    name.withStyle(info.contributorUUID != null ? ChatFormatting.AQUA : info.getRarity().getColour());
                }
                text.append(name);
                if(part.count > 1)
                {
                    text.append(Component.literal(" x" + part.count).withStyle(ChatFormatting.GRAY));
                }
                if(part.hasNew())
                {
                    text.append(Component.literal(" NEW").withStyle(ChatFormatting.GOLD));
                }
                graphics.text(font, text, getContentX(), getContentY() + 1, 0xFFFFFFFF, true);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
            {
                setSelected(this);
                select(part);
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ accessories

    private class AccessoryList extends ObjectSelectionList<AccessoryList.Entry>
    {
        AccessoryList(Minecraft minecraft, int width, int height, int y, int itemHeight)
        {
            super(minecraft, width, height, y, itemHeight);
        }

        void setHat(@Nullable HatPart hat)
        {
            List<Entry> entries = new ArrayList<>();
            if(hat != null)
            {
                HatInfo info = HatResources.get(hat);
                if(info != null)
                {
                    add(entries, info, hat, 0);
                }
            }
            replaceEntries(entries);
        }

        private void add(List<Entry> entries, HatInfo info, HatPart part, int depth)
        {
            for(HatPart child : part.hatParts)
            {
                HatInfo childInfo = null;
                for(HatInfo acc : info.accessories)
                {
                    if(acc.name.equals(child.name))
                    {
                        childInfo = acc;
                    }
                }
                if(childInfo == null)
                {
                    continue;
                }
                entries.add(new Entry(info, part, childInfo, child, depth));
                if(child.isShowing)
                {
                    add(entries, childInfo, child, depth + 1);
                }
            }
        }

        @Override
        public int getRowWidth()
        {
            return width - 12;
        }

        private class Entry extends ObjectSelectionList.Entry<Entry>
        {
            final HatInfo parentInfo;
            final HatPart parent;
            final HatInfo info;
            final HatPart part;
            final int depth;

            Entry(HatInfo parentInfo, HatPart parent, HatInfo info, HatPart part, int depth)
            {
                this.parentInfo = parentInfo;
                this.parent = parent;
                this.info = info;
                this.part = part;
                this.depth = depth;
            }

            @Override
            public Component getNarration()
            {
                return Component.literal(part.name);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a)
            {
                boolean unlocked = isUnlocked(part) && selected != null && isUnlocked(selected);
                MutableComponent text = Component.literal(part.isShowing ? "✔ " : "✖ ").withStyle(part.isShowing ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
                text.append(Component.literal(part.name).withStyle(unlocked ? info.getRarity().getColour() : ChatFormatting.DARK_GRAY));
                graphics.text(font, text, getContentX() + depth * 8, getContentY() + 1, 0xFFFFFFFF, true);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
            {
                if(selected == null || !isUnlocked(selected) || !isUnlocked(part))
                {
                    return true;
                }
                part.isShowing = !part.isShowing;
                if(part.isShowing && !info.accessoryLayer.isEmpty())
                {
                    //accessories on the same layer can't be worn together
                    for(HatPart sibling : parent.hatParts)
                    {
                        if(sibling == part || !sibling.isShowing)
                        {
                            continue;
                        }
                        HatInfo siblingInfo = parentInfo.getInfoFor(sibling.name);
                        if(siblingInfo != null && siblingInfo.accessoryLayer.stream().anyMatch(info.accessoryLayer::contains))
                        {
                            sibling.isShowing = false;
                        }
                    }
                }
                markCustomised();
                setHat(selected);
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ colour sliders

    private class ColourSlider extends AbstractSliderButton
    {
        private final String key;
        private final int index;

        ColourSlider(int x, int y, int width, int height, String key, int index)
        {
            super(x, y, width, height, Component.empty(), 0D);
            this.key = key;
            this.index = index;
            refresh();
        }

        void refresh()
        {
            if(selected == null)
            {
                value = index < 4 ? 1D : 0D;
            }
            else if(index < 4)
            {
                value = 1D - selected.colouriser[index];
            }
            else if(index == 4)
            {
                value = selected.hsbiser[0];
            }
            else
            {
                value = 1D - selected.hsbiser[index - 4];
            }
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            String shown = index == 4 ? Math.round(value * 360D) + "°" : Math.round(value * 100D) + "%";
            setMessage(Component.translatable(key).append(": " + shown));
        }

        @Override
        protected void applyValue()
        {
            if(selected == null)
            {
                return;
            }
            float v = (float)value;
            if(index < 4)
            {
                selected.colouriser[index] = 1F - v;
            }
            else if(index == 4)
            {
                selected.hsbiser[0] = v;
            }
            else
            {
                selected.hsbiser[index - 4] = 1F - v;
            }
            markCustomised();
        }
    }
}
