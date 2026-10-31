package dev.rawrland.constructionsite.client;

import dev.rawrland.constructionsite.content.cylinder.CylinderMountBlockEntity;
import dev.rawrland.constructionsite.network.CylinderSettingsPayload;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Settings window of a hydraulic cylinder (add-cylinder-settings). Opened with an empty
 * hand on either mount of a linked cylinder.
 *
 * Mode (decorative / driven), tube length, speed and redstone direction. While the
 * cylinder is decorative the other settings cannot be changed (owner, 29 October 2026).
 * Below them it shows the reach and, live, the current length and redstone.
 * The settings are sent to the server when the window closes.
 *
 * Plain Minecraft widgets for now; a Create-styled window can follow.
 */
public class HydraulicCylinderScreen extends Screen {

    private static final int WIDTH = 200;

    private final BlockPos pos;
    private boolean driven;
    private float tube;
    private float speed;
    private boolean inverted;
    private boolean loaded;

    private AbstractSliderButton tubeSlider;
    private AbstractSliderButton speedSlider;
    private CycleButton<Boolean> invertButton;

    private HydraulicCylinderScreen(BlockPos pos) {
        super(Component.translatable("gui.create_construction_site.hydraulic_cylinder.title"));
        this.pos = pos;
    }

    /** Opens the window for the mount at this position. Called from the block, in the player's game only. */
    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HydraulicCylinderScreen(pos));
    }

    private CylinderMountBlockEntity mount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof CylinderMountBlockEntity end && end.isLinked()) {
            return end;
        }
        return null;
    }

    @Override
    protected void init() {
        CylinderMountBlockEntity end = mount();
        if (end == null) {
            onClose();
            return;
        }
        if (!loaded) {
            driven = end.isDriven();
            tube = end.tubeLength();
            speed = end.speed();
            inverted = end.isInverted();
            loaded = true;
        }
        int x = (width - WIDTH) / 2;
        int y = height / 2 - 70;

        addRenderableWidget(CycleButton.booleanBuilder(
                Component.translatable("gui.create_construction_site.hydraulic_cylinder.driven"),
                Component.translatable("gui.create_construction_site.hydraulic_cylinder.decorative"))
            .withInitialValue(driven)
            .create(x, y, WIDTH, 20, Component.translatable("gui.create_construction_site.hydraulic_cylinder.mode"),
                (button, value) -> {
                    driven = value;
                    updateActive();
                }));

        tubeSlider = addRenderableWidget(new ValueSlider(x, y + 26, WIDTH,
            CylinderMountBlockEntity.MIN_TUBE, CylinderMountBlockEntity.MAX_TUBE, CylinderMountBlockEntity.TUBE_STEP,
            tube, "gui.create_construction_site.hydraulic_cylinder.tube", value -> tube = value));

        speedSlider = addRenderableWidget(new ValueSlider(x, y + 52, WIDTH,
            CylinderMountBlockEntity.MIN_SPEED, CylinderMountBlockEntity.MAX_SPEED, CylinderMountBlockEntity.SPEED_STEP,
            speed, "gui.create_construction_site.hydraulic_cylinder.speed", value -> speed = value));

        invertButton = addRenderableWidget(CycleButton.booleanBuilder(
                Component.translatable("gui.create_construction_site.hydraulic_cylinder.inverted"),
                Component.translatable("gui.create_construction_site.hydraulic_cylinder.normal"))
            .withInitialValue(inverted)
            .create(x, y + 78, WIDTH, 20, Component.translatable("gui.create_construction_site.hydraulic_cylinder.redstone"),
                (button, value) -> inverted = value));

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(x, y + 150, WIDTH, 20).build());

        updateActive();
    }

    /** Decorative: the other settings cannot be changed. */
    private void updateActive() {
        tubeSlider.active = driven;
        speedSlider.active = driven;
        invertButton.active = driven;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centre = width / 2;
        int y = height / 2 - 70;
        graphics.drawCenteredString(font, title, centre, y - 16, 0xFFFFFF);

        graphics.drawCenteredString(font, Component.translatable("gui.create_construction_site.hydraulic_cylinder.reach",
            format(tube), format(2 * tube)), centre, y + 108, 0xA0A0A0);

        CylinderMountBlockEntity end = mount();
        CylinderMountBlockEntity other = end == null ? null : end.partner();
        if (end != null && other != null && minecraft != null && minecraft.level != null) {
            CylinderMountBlockEntity barrel = end.isBarrelEnd() ? end : other;
            double length = Math.sqrt(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(minecraft.level,
                Vec3.atCenterOf(end.getBlockPos()), Vec3.atCenterOf(other.getBlockPos())));
            boolean outOfReach = length < tube - HydraulicCylinderRenderer.RED_SLACK
                || length > 2 * tube + HydraulicCylinderRenderer.RED_SLACK;
            graphics.drawCenteredString(font, Component.translatable("gui.create_construction_site.hydraulic_cylinder.now",
                format(length), barrel.signal()), centre, y + 122, outOfReach ? 0xFF6060 : 0xA0A0A0);
            if (outOfReach) {
                graphics.drawCenteredString(font, Component.translatable(
                    "gui.create_construction_site.hydraulic_cylinder.out_of_reach"), centre, y + 134, 0xFF6060);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        // The game keeps running, so the arm can be watched while setting it up.
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (loaded) {
            PacketDistributor.sendToServer(new CylinderSettingsPayload(pos, driven, tube, speed, inverted));
        }
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }

    /** A slider in fixed steps between a minimum and a maximum, showing "Name: value". */
    private static final class ValueSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final float step;
        private final String key;
        private final java.util.function.Consumer<Float> onChange;

        ValueSlider(int x, int y, int width, float min, float max, float step, float current, String key,
                    java.util.function.Consumer<Float> onChange) {
            super(x, y, width, 20, Component.empty(), (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.key = key;
            this.onChange = onChange;
            updateMessage();
        }

        private float current() {
            float raw = min + (float) value * (max - min);
            return Mth.clamp(Math.round(raw / step) * step, min, max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, String.format("%.1f", current())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
