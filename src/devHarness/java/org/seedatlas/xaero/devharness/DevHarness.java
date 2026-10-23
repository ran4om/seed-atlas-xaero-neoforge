package org.seedatlas.xaero.devharness;

import com.mojang.blaze3d.platform.Window;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.seedatlas.xaero.config.SeedAtlasClientState;
import org.seedatlas.xaero.gui.BiomeHighlightScreen;
import org.seedatlas.xaero.gui.MarkerSettingsScreen;
import org.seedatlas.xaero.gui.NearestScreen;
import org.seedatlas.xaero.gui.PerformanceSettingsScreen;
import org.seedatlas.xaero.gui.SeedAtlasSettingsScreen;
import org.seedatlas.xaero.gui.WorldGenerationScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * Development only, loaded by the {@code runHarness} task: creates a seeded creative world, runs
 * the Seed Atlas client commands, opens Xaero's map in all three dimensions, takes screenshots
 * and quits. Every step logs a "[HARNESS]" line.
 */
@Mod(value = "seedatlas_devharness", dist = Dist.CLIENT)
public final class DevHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeedAtlasHarness");
    private static final long SEED = 8675309L;
    private static final int MAP_WAIT = 300;
    private static final int TIMEOUT = 20 * 60 * 6;

    private final List<Step> steps = new ArrayList<>();
    private int step;
    private int wait;
    private int ticks;
    private boolean started;

    public DevHarness() {
        String[] dimensions = {"overworld", "the_nether", "the_end"};
        steps.add(new Step("create world", 0, this::createWorld));
        steps.add(new Step("commands", 200, () -> {
            command("seedatlas status");
            command("seedatlas version");
        }));
        for (String dimension : dimensions) {
            if (!dimension.equals("overworld")) {
                steps.add(new Step("close map", 20, () -> Minecraft.getInstance().setScreen(null)));
                steps.add(new Step("teleport " + dimension, 20, () ->
                    command("execute in minecraft:" + dimension + " run tp @s 0 " + (dimension.equals("the_end") ? 70 : 64) + " 0")));
            }
            steps.add(new Step("open map " + dimension, 200, this::openMap));
            steps.add(new Step("screenshot " + dimension, MAP_WAIT, () -> screenshot(dimension + "-map")));
            steps.add(new Step("zoom out " + dimension, 5, () -> zoom(-5)));
            steps.add(new Step("zoom out more " + dimension, 20, () -> zoom(-5)));
            steps.add(new Step("screenshot " + dimension + " zoomed out", MAP_WAIT, () -> screenshot(dimension + "-map-zoomed-out")));
            if (dimension.equals("overworld")) addMenus();
        }
        steps.add(new Step("status", 20, () -> {
            Minecraft.getInstance().setScreen(null);
            command("seedatlas status");
        }));
        steps.add(new Step("settings", 40, () -> command("seedatlas settings")));
        steps.add(new Step("screenshot settings", 40, () -> screenshot("settings")));
        steps.add(new Step("quit", 40, () -> {
            LOGGER.info("[HARNESS] done");
            Minecraft.getInstance().stop();
        }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    /** Clicks the SA button on the map, then opens every Seed Atlas menu over the map and screenshots each. */
    private void addMenus() {
        steps.add(new Step("click SA button", 5, DevHarness::clickSettingsButton));
        steps.add(new Step("check settings opened", 5, () -> {
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof SeedAtlasSettingsScreen)) {
                LOGGER.error("[HARNESS] the SA button opened {} instead of the settings", screen);
                SeedAtlasClientState.openSettings(screen);
            }
        }));
        steps.add(new Step("screenshot menu settings", 40, () -> screenshot("menu-settings")));
        List<Function<Screen, Screen>> menus = List.of(WorldGenerationScreen::new, MarkerSettingsScreen::new,
            PerformanceSettingsScreen::new, BiomeHighlightScreen::new, parent -> new NearestScreen(parent, false),
            parent -> new NearestScreen(parent, true));
        String[] names = {"world-generation", "markers", "performance", "biome-highlights", "nearest-structure", "nearest-biome"};
        for (int i = 0; i < menus.size(); ++i) {
            Function<Screen, Screen> menu = menus.get(i);
            String name = names[i];
            steps.add(new Step("open menu " + name, 5, () -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.setScreen(menu.apply(minecraft.screen));
            }));
            steps.add(new Step("screenshot menu " + name, 40, () -> screenshot("menu-" + name)));
            steps.add(new Step("back from " + name, 5, () -> Minecraft.getInstance().screen.onClose()));
        }
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (++ticks > TIMEOUT) {
            LOGGER.error("[HARNESS] timed out at step {}", steps.get(step).name);
            minecraft.stop();
            return;
        }
        if (!started) {
            if (!(minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen)) return;
            started = true;
        }
        if (step > 0 && (minecraft.player == null || minecraft.level == null)) return;
        if (step >= steps.size() || --wait > 0) return;
        Step current = steps.get(step++);
        LOGGER.info("[HARNESS] {}", current.name);
        try {
            current.action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("[HARNESS] step failed: {}", current.name, exception);
        }
        wait = step < steps.size() ? steps.get(step).delay : 0;
    }

    private void createWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelSettings settings = new LevelSettings("harness", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel("harness", settings, new WorldOptions(SEED, true, false),
            WorldPresets::createNormalWorldDimensions, minecraft.screen);
    }

    private static void command(String command) {
        LOGGER.info("[HARNESS] /{}", command);
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    private void openMap() {
        Minecraft minecraft = Minecraft.getInstance();
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) {
            LOGGER.warn("[HARNESS] Xaero's map session is not ready; retrying");
            --step;
            wait = 40;
            return;
        }
        minecraft.setScreen(new GuiMap(null, null, session.getMapProcessor(), minecraft.getCameraEntity()));
    }

    private static void zoom(int clicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return;
        double x = minecraft.screen.width / 2.0;
        double y = minecraft.screen.height / 2.0;
        for (int i = 0; i < Math.abs(clicks); ++i) minecraft.screen.mouseScrolled(x, y, 0, Math.signum(clicks));
    }

    /**
     * Presses and releases the mouse on the map's SA button through Minecraft's own mouse handler,
     * so every mod listening to screen mouse events sees the click, as in a real game.
     */
    private static void clickSettingsButton() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen map = minecraft.screen;
        // The layer toggle at the bottom is labelled SA as well; the settings button is at the top.
        AbstractWidget button = map.children().stream()
            .filter(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().equals("SA")
                && widget.getY() == 0)
            .map(AbstractWidget.class::cast).findFirst()
            .orElseThrow(() -> new IllegalStateException("No SA button on " + map));
        Window window = minecraft.getWindow();
        double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        try {
            Field x = MouseHandler.class.getDeclaredField("xpos");
            Field y = MouseHandler.class.getDeclaredField("ypos");
            Method press = MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
            x.setAccessible(true);
            y.setAccessible(true);
            press.setAccessible(true);
            x.setDouble(minecraft.mouseHandler, (button.getX() + button.getWidth() / 2.0) * scale);
            y.setDouble(minecraft.mouseHandler, (button.getY() + button.getHeight() / 2.0) * scale);
            press.invoke(minecraft.mouseHandler, window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
            press.invoke(minecraft.mouseHandler, window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Clicking the SA button failed", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void screenshot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        Screenshot.grab(minecraft.gameDirectory, "harness-" + name + ".png", minecraft.getMainRenderTarget(),
            message -> LOGGER.info("[HARNESS] {}", message.getString()));
    }

    private record Step(String name, int delay, Runnable action) { }
}
