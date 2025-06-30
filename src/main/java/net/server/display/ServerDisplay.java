package net.server.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
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

@Mod("server_display")
public class ServerDisplay {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config;
    private static String playerCount;
    private static String playerExp;
    private static String typingText = "";
    private static int typingIndex;
    private static long lastTypingTime;

    public ServerDisplay() {
        NeoForge.EVENT_BUS.register(this);
        config = new Config(new ModConfigSpec.Builder()).build();
        loadConfig();
        PingService.init();
    }

    private static void loadConfig() {
        File configDir = new File("config/server_display");
        if (!configDir.exists() && !configDir.mkdirs()) {
            LOGGER.error("Failed to create config directory");
        }

        File configFile = new File(configDir, "config.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                config = GSON.fromJson(json, Config.class);
            } catch (IOException e) {
                LOGGER.error("Failed to load config", e);
            }
        } else {
            config = new Config(new ModConfigSpec.Builder()).build();
            saveConfig();
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
        if (mc.level == null) return;

        playerCount = String.valueOf(mc.level.players().size());

        if (mc.player != null) {
            playerExp = String.valueOf((int)(mc.player.experienceProgress * 100));
        }

        if (config.typingEffect && !config.description.isEmpty()) {
            if (System.currentTimeMillis() - lastTypingTime > config.typingSpeed) {
                if (typingIndex < config.description.length()) {
                    typingText += config.description.charAt(typingIndex++);
                    lastTypingTime = System.currentTimeMillis();
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!config.enabled) return;

        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        int boxWidth = 120;
        int boxHeight = config.showDescription ? 160 : 120;
        int x = width - boxWidth - 10;
        int y = height / 2 - boxHeight / 2;

        renderBackground(gui, x, y, boxWidth, boxHeight);
        renderContent(gui, x, y, boxWidth);
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
            gui.drawString(mc.font, Component.literal(config.title), x + w/2 - mc.font.width(config.title)/2, textY, 0xFFFFFF);
            textY += 25;
        }

        String tpsInfo = "20.0";
        gui.drawString(mc.font, Component.literal("TPS: " + tpsInfo), x+5, textY, 0xFFFFFF);
        textY += 20;

        gui.drawString(mc.font, Component.literal("延迟: " + PingService.getLatency()), x+5, textY, 0xFFFFFF);
        textY += 20;

        gui.drawString(mc.font, Component.literal("玩家: " + playerCount), x+5, textY, 0xFFFFFF);
        textY += 20;

        if (config.showExp) {
            gui.drawString(mc.font, Component.literal("经验: " + playerExp + "%"), x+5, textY, 0xFFFFFF);
            textY += 20;
        }

        if (config.showDescription && !config.description.isEmpty()) {
            String desc = config.typingEffect ? typingText : config.description;
            gui.drawString(mc.font, Component.literal(desc), x+5, textY, 0xFFFFFF);
        }
    }

    public static class Config {
        public final boolean enabled;
        public final boolean showTitle;
        public final boolean showExp;
        public final boolean showDescription;
        public final boolean typingEffect;
        public final String title;
        public final String description;
        public final int typingSpeed;

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

        public Config build() {
            return this;
        }
    }
}