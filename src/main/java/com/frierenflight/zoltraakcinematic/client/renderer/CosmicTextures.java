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
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OpenGL sky and space textures with mipmaps and spherical wrapping.
 */
public final class CosmicTextures {
    private static final Map<String, Integer> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private CosmicTextures() {}

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

        int uploadedId = uploadTexture(fileName);
        if (uploadedId > 0) {
            TEXTURE_CACHE.put(fileName, uploadedId);
        }
        return uploadedId;
    }

    private static int uploadTexture(String fileName) {
        try {
            BufferedImage image = loadImage(fileName);
            if (image == null) {
                System.err.println("[ZoltraakCinematic] Cosmic texture " + fileName + " could not be decoded.");
                return 0;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);

            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                    buffer.put((byte) (pixel & 0xFF));         // B
                    buffer.put((byte) 255);                    // A
                }
            }
            buffer.flip();

            int textureId = GlStateManager._genTexture();
            GlStateManager._bindTexture(textureId);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // Anisotropic filtering if supported
            try {
                if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                    GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, 8.0f);
                }
            } catch (Throwable ignored) {}

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

            GlStateManager._bindTexture(0);
            System.out.println("[ZoltraakCinematic] Successfully uploaded cosmic texture " + fileName + " (" + width + "x" + height + ") to OpenGL [id=" + textureId + "]");
            return textureId;
        } catch (Exception e) {
            System.err.println("[ZoltraakCinematic] Failed to load and upload cosmic texture " + fileName + ": " + e.getMessage());
            return 0;
        }
    }

    private static BufferedImage loadImage(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getResourceManager() != null) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/cosmic/" + fileName);
            Optional<Resource> resource = mc.getResourceManager().getResource(loc);
            if (resource.isPresent()) {
                try (InputStream is = resource.get().open()) {
                    return ImageIO.read(is);
                } catch (Exception e) {
                    
                }
            }
        }

        String path = "/assets/" + ZoltraakCinematicMod.MODID + "/textures/cosmic/" + fileName;
        try (InputStream is = CosmicTextures.class.getResourceAsStream(path)) {
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            
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
    }
}
