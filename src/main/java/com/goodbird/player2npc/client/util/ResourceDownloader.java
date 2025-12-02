package com.goodbird.player2npc.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ResourceDownloader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<Identifier> active = Collections.synchronizedSet(new HashSet<>());
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public ResourceDownloader() {
    }

    public static void load(ImageDownloadAlt resource) {
        if (!active.add(resource.location)) {
            return;
        }

        executor.execute(() -> {
            try {
                resource.loadTextureFromServer();
                MinecraftClient client = MinecraftClient.getInstance();
                client.submit(() -> {
                    client.getTextureManager().registerTexture(resource.location, resource);
                    active.remove(resource.location);
                });
            } catch (Exception e) {
                LOGGER.error("Failed to queue skin download for {}", resource.location, e);
                active.remove(resource.location);
            }
        });
    }

    public static Identifier getUrlResourceLocation(String url, boolean fixSkin) {
        String var10002 = "cnpcaicompanion";
        int var10003 = (url + fixSkin).hashCode();
        return new Identifier(var10002, "skins/" + var10003 + (fixSkin ? "" : "32"));
    }

    public static File getUrlFile(String url, boolean fixSkin) {
        File var10002;
        try {
            var10002 = (File) PlayerSkinProvider.class.getField("skinCacheDir").get(MinecraftClient.getInstance().getSkinProvider());
        } catch (Exception e) {
            var10002 = new File(MinecraftClient.getInstance().runDirectory, "cache");
        }
        String var10003 = url + fixSkin;
        return new File(var10002, "" + var10003.hashCode());
    }

    public static boolean contains(Identifier location) {
        return active.contains(location);
    }
}
