package net.server.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mod("server_display")
public class ServerDisplay {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config;
    private static final AtomicReference<String> playerCount = new AtomicReference<>("0");
    private static final AtomicReference<String> playerExp = new AtomicReference<>("0");
    private static final AtomicReference<String> typingText = new AtomicReference<>("");
    private static final AtomicInteger typingIndex = new AtomicInteger(0);
    private static volatile long lastTypingTime;
    private static final int TYPING_RESET_DELAY = 5000;

    public ServerDisplay() {
        NeoForge.EVENT_BUS.register(this);
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        config = new Config(builder);
        builder.build();
    }

    @SubscribeEvent
    public void onClientSetup(FMLClientSetupEvent event) {
        loadConfig();
        PingService.init();
    }

    private static void loadConfig() {
        File configDir = new File("config/server_display");
        try {
            if (!configDir.exists()) {
                Files.createDirectories(configDir.toPath());
            }

            File configFile = new File(configDir, "config.json");
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    config = GSON.fromJson(json, Config.class);
                }
            } else {
                saveConfig();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load/save config", e);
        }
    }

    private static void saveConfig() {
        File configFile = new File("config/server_display/config.json");
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            playerCount.set("0");
            playerExp.set("0");
            return;
        }

        playerCount.set(String.valueOf(mc.level.players().size()));

        if (mc.player != null) {
            playerExp.set(String.valueOf((int)(mc.player.experienceProgress * 100)));
        }

        if (config.typingEffect && !config.description.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTypingTime > config.typingSpeed) {
                if (typingIndex.get() < config.description.length()) {
                    typingText.updateAndGet(s -> s + config.description.charAt(typingIndex.getAndIncrement()));
                    lastTypingTime = currentTime;
                } else if (currentTime - lastTypingTime > TYPING_RESET_DELAY) {
                    typingText.set("");
                    typingIndex.set(0);
                    lastTypingTime = currentTime;
                }
            }
        } else {
            typingText.set(config.description);
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!config.enabled || Minecraft.getInstance().options.hideGui) return;

        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        int boxWidth = 120;
        int boxHeight = calculateBoxHeight();
        int x = width - boxWidth - 10;
        int y = height / 2 - boxHeight / 2;

        renderBackground(gui, x, y, boxWidth, boxHeight);
        renderContent(gui, x, y, boxWidth);
    }

    private int calculateBoxHeight() {
        int height = 80;
        if (config.showTitle) height += 25;
        if (config.showExp) height += 20;
        if (config.showDescription) height += 40;
        return height;
    }

    private void renderBackground(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x-2, y-2, x+w+2, y+h+2, 0xFF000000);
        for (int i = 0; i < h; i += 22) {
            gui.fill(x, y+i, x+w, y+Math.min(i+20, h), 0x80000000);
        }
    }

    private void renderContent(GuiGraphics gui, int x, int y, int w) {
        Minecraft mc = Minecraft.getInstance();
        int textY = y + 10;

        if (config.showTitle) {
            gui.drawCenteredString(mc.font, Component.literal(config.title), x + w/2, textY, 0xFFFFFF);
            textY += 25;
        }

        String tpsInfo = "20.0";
        gui.drawString(mc.font, Component.literal("TPS: " + tpsInfo), x+5, textY, 0xFFFFFF);
        textY += 20;

        gui.drawString(mc.font, Component.literal("延迟: " + PingService.getLatency()), x+5, textY, 0xFFFFFF);
        textY += 20;

        gui.drawString(mc.font, Component.literal("玩家: " + playerCount.get()), x+5, textY, 0xFFFFFF);
        textY += 20;

        if (config.showExp) {
            gui.drawString(mc.font, Component.literal("经验: " + playerExp.get() + "%"), x+5, textY, 0xFFFFFF);
            textY += 20;
        }

        if (config.showDescription && !config.description.isEmpty()) {
            String desc = config.typingEffect ? typingText.get() : config.description;
            gui.renderTooltip(mc.font, mc.font.split(Component.literal(desc), w-10), x+5, textY);
        }
    }

    public static class Config {
        public boolean enabled;
        public boolean showTitle;
        public boolean showExp;
        public boolean showDescription;
        public boolean typingEffect;
        public String title;
        public String description;
        public int typingSpeed;

        public Config(ModConfigSpec.Builder builder) {
            builder.push("general");
            enabled = builder.define("enabled", true).get();
            showTitle = builder.define("showTitle", true).get();
            showExp = builder.define("showExp", false).get();
            showDescription = builder.define("showDescription", true).get();
            typingEffect = builder.define("typingEffect", true).get();
            title = builder.define("title", "服务器信息").get();
            description = builder.define("description", "欢迎来到服务器！").get();
            typingSpeed = builder.defineInRange("typingSpeed", 100, 50, 500).get();
            builder.pop();
        }
    }
}
