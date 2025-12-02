package com.goodbird.player2npc.client.util;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.texture.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Environment(EnvType.CLIENT)
public class ImageDownloadAlt extends ResourceTexture {
    private static final Logger logger = LogManager.getLogger();
    public final File cacheFile;
    private final String imageUrl;
    private final boolean fix64;
    private final Runnable r;
    public final Identifier location;
    public boolean uploaded = false;

    public ImageDownloadAlt(File file, String url, Identifier location, Identifier defaultLocation, boolean fix64, Runnable r) {
        super(defaultLocation);
        this.location = location;
        this.cacheFile = file;
        this.imageUrl = url;
        this.fix64 = fix64;
        this.r = r;
    }

    public void setImage(NativeImage image) {
        MinecraftClient.getInstance().execute(() -> {
            this.uploaded = true;
            if (!RenderSystem.isOnRenderThread()) {
                RenderSystem.recordRenderCall(() -> this.upload(image));
            } else {
                this.upload(image);
            }

            this.r.run();
        });
    }

    private void upload(NativeImage imageIn) {
        TextureUtil.prepareImage(this.getGlId(), imageIn.getWidth(), imageIn.getHeight());
        imageIn.upload(0, 0, 0, true);
    }

    public void load(ResourceManager resourceManager) throws IOException {
        if (this.cacheFile != null && this.cacheFile.isFile()) {
            logger.debug("Loading http texture from local cache ({})", this.cacheFile);

            try (FileInputStream inputStream = new FileInputStream(this.cacheFile);
                 NativeImage image = NativeImage.read(inputStream)) {
                this.setImage(this.parseUserSkin(image));
                return;
            } catch (IOException ioexception) {
                logger.error("Couldn't load skin {}", this.cacheFile, ioexception);
            }
        }

        if (!this.uploaded) {
            this.uploaded = true;
            super.load(resourceManager);
        }

    }

    public void loadTextureFromServer() {
        HttpURLConnection connection = null;
        logger.debug("Downloading http texture from {} to {}", new Object[]{this.imageUrl, this.cacheFile});

        try {
            connection = (HttpURLConnection) (new URL(this.imageUrl)).openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 5.1; rv:19.0) Gecko/20100101 Firefox/19.0");
            connection.connect();
            String type = connection.getContentType();
            long size = connection.getContentLengthLong();
            if (connection.getResponseCode() / 100 == 2 && type.equals("image/png") && (size <= 2000000L || MinecraftClient.getInstance().isIntegratedServerRunning())) {
                FileUtils.copyInputStreamToFile(connection.getInputStream(), this.cacheFile);
            }
        } catch (IOException | RuntimeException exception) {
            logger.error("Couldn't download http texture", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }

        }

    }

    public NativeImage parseUserSkin(NativeImage image) {
        if (image.getHeight() != image.getWidth() && image.getWidth() / 2 != image.getHeight()) {
            int var10002 = image.getWidth();
            throw new IllegalArgumentException("Invalid texture size: " + var10002 + "x" + image.getHeight());
        } else {
            int scale = image.getWidth() / 64;
            boolean lvt_2_1_ = image.getHeight() != image.getWidth();
            if (lvt_2_1_ && this.fix64) {
                NativeImage nativeImage = new NativeImage(64 * scale, 64 * scale, true);
                nativeImage.copyFrom(image);
                image.close();
                image = nativeImage;
                nativeImage.fillRect(0, 32 * scale, 64 * scale, 32 * scale, 0);
                nativeImage.copyRectangle(4 * scale, 16 * scale, 16 * scale, 32 * scale, 4 * scale, 4 * scale, true, false);
                nativeImage.copyRectangle(8 * scale, 16 * scale, 16 * scale, 32 * scale, 4 * scale, 4 * scale, true, false);
                nativeImage.copyRectangle(0, 20 * scale, 24 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(4 * scale, 20 * scale, 16 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(8 * scale, 20 * scale, 8 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(12 * scale, 20 * scale, 16 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(44 * scale, 16 * scale, -8 * scale, 32 * scale, 4 * scale, 4 * scale, true, false);
                nativeImage.copyRectangle(48 * scale, 16 * scale, -8 * scale, 32 * scale, 4 * scale, 4 * scale, true, false);
                nativeImage.copyRectangle(40 * scale, 20 * scale, 0, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(44 * scale, 20 * scale, -8 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(48 * scale, 20 * scale, -16 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
                nativeImage.copyRectangle(52 * scale, 20 * scale, -8 * scale, 32 * scale, 4 * scale, 12 * scale, true, false);
            }

            if (lvt_2_1_ && this.fix64) {
                setAreaTransparent(image, 32 * scale, 0, 64 * scale, 32 * scale);
            }

            return image;
        }
    }

    private static void setAreaTransparent(NativeImage image, int x, int y, int width, int height) {
        for (int i = x; i < width; ++i) {
            for (int j = y; j < height; ++j) {
                int k = image.getPixelColor(i, j);
                if ((k >> 24 & 255) < 128) {
                    return;
                }
            }
        }

        for (int l = x; l < width; ++l) {
            for (int i1 = y; i1 < height; ++i1) {
                image.setPixelColor(l, i1, image.getPixelColor(l, i1) & 16777215);
            }
        }

    }
}
