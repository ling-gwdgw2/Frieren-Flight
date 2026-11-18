// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages OpenGL sky and space textures with mipmaps, spherical wrapping,
 * and asynchronous off-thread decoding to avoid render-thread hitches.
 */
public final class CosmicTextures {
    private static final int MAX_COSMIC_WIDTH = 2048;
    private static final int MAX_COSMIC_HEIGHT = 1024;

    private record DecodedTexture(int width, int height, ByteBuffer rgba) {}

    private static final Map<String, Integer> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<DecodedTexture>> PENDING_DECODES = new ConcurrentHashMap<>();
    private static final AtomicBoolean PRELOAD_STARTED = new AtomicBoolean(false);

    private CosmicTextures() {}

    public static void preloadAsync() {
        if (!PRELOAD_STARTED.compareAndSet(false, true)) {
            return;
        }
        getOrStartAsyncDecode("milky_way.jpg");
    }

    public static int getMilkyWayTextureId() {
        return getTextureId("milky_way.jpg");
    }

    public static int getTextureId(String fileName) {
        if (!RenderSystem.isOnRenderThread()) {
            return TEXTURE_CACHE.getOrDefault(fileName, 0);
        }

        Integer existing = TEXTURE_CACHE.get(fileName);
        if (existing != null && existing > 0) {
            return existing;
        }

        CompletableFuture<DecodedTexture> future = getOrStartAsyncDecode(fileName);
        // Never block the render thread decoding a large JPEG: finish upload only once ready,
        // so the frame Gargantua spawns stays hitch-free.
        if (!future.isDone()) {
            return 0;
        }

        DecodedTexture decoded = future.getNow(null);
        PENDING_DECODES.remove(fileName);
        if (decoded == null) {
            return 0;
        }

        int uploadedId = uploadDecodedTexture(fileName, decoded);
        if (uploadedId > 0) {
            TEXTURE_CACHE.put(fileName, uploadedId);
        }
        return uploadedId;
    }

    private static CompletableFuture<DecodedTexture> getOrStartAsyncDecode(String fileName) {
        return PENDING_DECODES.computeIfAbsent(fileName, key -> CompletableFuture.supplyAsync(() -> decodeOffThread(key)));
    }

    private static DecodedTexture decodeOffThread(String fileName) {
        try {
            BufferedImage image = loadImage(fileName);
            if (image == null) {
                System.err.println("[ZoltraakCinematic] Cosmic texture " + fileName + " could not be decoded.");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            if (width > MAX_COSMIC_WIDTH || height > MAX_COSMIC_HEIGHT) {
                double scale = Math.min((double) MAX_COSMIC_WIDTH / width, (double) MAX_COSMIC_HEIGHT / height);
                int targetW = Math.max(1, (int) Math.round(width * scale));
                int targetH = Math.max(1, (int) Math.round(height * scale));
                BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, targetW, targetH, null);
                g.dispose();
                image = scaled;
                width = targetW;
                height = targetH;
            } else if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = converted.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
                image = converted;
            }

            byte[] bgr = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            for (int i = 0; i + 2 < bgr.length; i += 3) {
                buffer.put(bgr[i + 2]); // R
                buffer.put(bgr[i + 1]); // G
                buffer.put(bgr[i]);     // B
                buffer.put((byte) 255); // A
            }
            buffer.flip();
            return new DecodedTexture(width, height, buffer);
        } catch (Exception e) {
            System.err.println("[ZoltraakCinematic] Failed to decode cosmic texture " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private static int uploadDecodedTexture(String fileName, DecodedTexture decoded) {
        try {
            int textureId = GlStateManager._genTexture();
            GlStateManager._bindTexture(textureId);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            try {
                if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                    GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, 4.0f);
                }
            } catch (Throwable ignored) {}

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, decoded.width, decoded.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, decoded.rgba);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

            GlStateManager._bindTexture(0);
            System.out.println("[ZoltraakCinematic] Uploaded cosmic texture " + fileName + " (" + decoded.width + "x" + decoded.height + ") to OpenGL [id=" + textureId + "]");
            return textureId;
        } catch (Exception e) {
            System.err.println("[ZoltraakCinematic] Failed to upload cosmic texture " + fileName + ": " + e.getMessage());
            return 0;
        }
    }

    private static BufferedImage loadImage(String fileName) {
        String path = "/assets/" + ZoltraakCinematicMod.MODID + "/textures/cosmic/" + fileName;
        try (InputStream is = CosmicTextures.class.getResourceAsStream(path)) {
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getResourceManager() != null) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/cosmic/" + fileName);
            Optional<Resource> resource = mc.getResourceManager().getResource(loc);
            if (resource.isPresent()) {
                try (InputStream is = resource.get().open()) {
                    return ImageIO.read(is);
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(CosmicTextures::clear);
            return;
        }
        for (int id : TEXTURE_CACHE.values()) {
            if (id > 0) {
                GlStateManager._deleteTexture(id);
            }
        }
        TEXTURE_CACHE.clear();
        PENDING_DECODES.clear();
        PRELOAD_STARTED.set(false);
    }
}
