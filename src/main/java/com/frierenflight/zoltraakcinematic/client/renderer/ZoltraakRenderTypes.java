// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ZoltraakRenderTypes extends RenderType {
   private static final ResourceLocation TEX_GLOW = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/spell/zoltraak_glow.png");
   public static final ResourceLocation TEX_MAGIC_CIRCLE = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/spell/zoltraak_magic_circle.png");
   public static final ResourceLocation TEX_WHITE = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/spell/zoltraak_white.png");
   private static final ResourceLocation TEX_CIRCLE = TEX_MAGIC_CIRCLE;
   private static final Map<ResourceLocation, RenderType> IRIS_EMISSIVE_TEXTURED_CACHE = new ConcurrentHashMap<>();

   // Use vanilla LIGHTNING_TRANSPARENCY so Iris / Sodium / shaderpacks recognize the additive blending
   // and automatically route it to the emissive glowing HDR bloom pipeline.
   private static final TransparencyStateShard ADD = LIGHTNING_TRANSPARENCY;

   private static ShaderStateShard safeShader(Supplier<ShaderInstance> primary) {
      return new ShaderStateShard(() -> {
         ShaderInstance s = primary.get();
         return s != null ? s : GameRenderer.getPositionTexColorShader();
      });
   }

   private static ShaderStateShard safeShaderColor(Supplier<ShaderInstance> primary) {
      return new ShaderStateShard(() -> {
         ShaderInstance s = primary.get();
         return s != null ? s : GameRenderer.getPositionColorShader();
      });
   }

   private static ShaderStateShard safeShaderTexColorNormal(Supplier<ShaderInstance> primary) {
      return new ShaderStateShard(() -> {
         ShaderInstance s = primary.get();
         return s != null ? s : GameRenderer.getRendertypeCloudsShader();
      });
   }

   public static final RenderType LIGHT = create(
      "zol_light",
      DefaultVertexFormat.POSITION_COLOR,
      Mode.TRIANGLES,
      262144,
      false,
      false,
      CompositeState.builder()
         .setShaderState(POSITION_COLOR_SHADER)
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType DEFENSE_SURFACE = create(
      "zol_defense_surface",
      DefaultVertexFormat.POSITION_COLOR,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderColor(() -> ZoltraakCinematicShaders.defenseBarrier))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType CORONA = create(
      "zol_corona",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      4096,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.corona))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType ZOL_CIRCLE = create(
      "zol_circle",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.magicCircle))
         .setTextureState(new TextureStateShard(TEX_CIRCLE, true, false))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType ZOL_GLOW = create(
      "zol_glow",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.cinematicFlare))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType ZOL_IMPACT = create(
      "zol_impact",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      16384,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.zoltraakImpact))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_IMPACT = create(
      "black_zol_impact",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      16384,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.zoltraakImpact))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType ZOL_BLAST_DOME = create(
      "zol_blast_dome",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.zoltraakBlastDome))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_BLAST_DOME = create(
      "black_zol_blast_dome",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.zoltraakBlastDome))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType ZOL_PYROCLAST = create(
      "zol_pyroclast",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.zoltraakPyroclast))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType EXPLOSION_VORTEX_RING = create(
      "explosion_vortex_ring",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      16384,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.explosionVortexRing))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType EXPLOSION_DOME = create(
      "explosion_dome",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.explosionDome))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType EXPLOSION_DOME_HALO = create(
      "explosion_dome_halo",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.explosionDome))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType EXPLOSION_DUST_WAVE = create(
      "explosion_dust_wave",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      16384,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.explosionDustWave))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType EXPLOSION_PYROCLAST = create(
      "explosion_pyroclast",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.explosionPyroclast))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType ZOL_BEAM = create(
      "zol_beam",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.zoltraak))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_CIRCLE = create(
      "black_zol_circle",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.blackMagicCircle))
         .setTextureState(new TextureStateShard(TEX_CIRCLE, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_GLOW = create(
      "black_zol_glow",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.cinematicFlare))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_CORE = create(
      "black_zol_core",
      DefaultVertexFormat.POSITION_COLOR,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(POSITION_COLOR_SHADER)
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType GREAT_WHITE = create(
      "great_zol_white",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.greatWhite))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType GREAT_PLUME = create(
      "great_zol_plume",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.greatPlume))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType RAPID_BULLET = create(
      "rapid_zol_bullet",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.rapidBullet))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_RAPID_BULLET = create(
      "black_rapid_zol_bullet",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.rapidBullet))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType GREAT_BLACK = create(
      "great_zol_black",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.greatBlack))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BLACK_BEAM = create(
      "black_zol_beam",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.blackZoltraak))
         .setTextureState(new TextureStateShard(TEX_GLOW, true, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BARRAGE_CIRCLE = ZOL_CIRCLE;

   public static final RenderType GARGANTUA_DISK_PREVIEW = create(
      "gargantua_disk_preview",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.gargantuaDiskPreview))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType GARGANTUA_SPHERE_PREVIEW = create(
      "gargantua_sphere_preview",
      DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
      Mode.TRIANGLES,
      32768,
      false,
      false,
      CompositeState.builder()
         .setShaderState(safeShaderTexColorNormal(() -> ZoltraakCinematicShaders.gargantuaSpherePreview))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static final RenderType BARRAGE_ATLAS = create(
      "zol_barrage_atlas",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      4096,
      false,
      true,
      CompositeState.builder()
         .setShaderState(safeShader(() -> ZoltraakCinematicShaders.barrageFx))
         .setTextureState(new TextureStateShard(TEX_GLOW, false, false))
         .setTransparencyState(ADD)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   public static RenderType magicAdditive(ResourceLocation texture) {
      return create(
         "zol_magic_additive",
         DefaultVertexFormat.POSITION_TEX_COLOR,
         Mode.QUADS,
         4096,
         false,
         true,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
            .setTextureState(new TextureStateShard(texture, false, false))
            .setTransparencyState(ADD)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
      );
   }

   public static RenderType magicTranslucent(ResourceLocation texture) {
      return create(
         "zol_magic_translucent",
         DefaultVertexFormat.POSITION_TEX_COLOR,
         Mode.QUADS,
         4096,
         false,
         true,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
            .setTextureState(new TextureStateShard(texture, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
      );
   }

   public static RenderType unsealingCircle(ResourceLocation texture) {
      return create(
         "zol_unsealing_circle",
         DefaultVertexFormat.POSITION_TEX_COLOR,
         Mode.QUADS,
         4096,
         false,
         true,
         CompositeState.builder()
            .setShaderState(safeShader(() -> ZoltraakCinematicShaders.unsealingMagicCircle))
            .setTextureState(new TextureStateShard(texture, false, false))
            .setTransparencyState(ADD)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
      );
   }

   public static RenderType unsealingFireBeam(ResourceLocation texture) {
      return create(
         "zol_unsealing_fire_beam",
         DefaultVertexFormat.POSITION_TEX_COLOR,
         Mode.QUADS,
         4096,
         false,
         true,
         CompositeState.builder()
            .setShaderState(safeShader(() -> ZoltraakCinematicShaders.unsealingFireColumn))
            .setTextureState(new TextureStateShard(texture, false, false))
            .setTransparencyState(ADD)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
      );
   }

   /**
    * Iris Dual-Pass Phase 1 RenderType for alpha-masked rune circles and textured spell geometry.
    * Uses RENDERTYPE_EYES_SHADER (Iris gbuffers_spidereyes) + LIGHTNING_TRANSPARENCY (SRC_ALPHA, ONE)
    * so shaderpacks such as Complementary Reimagined apply full emissive HDR bloom and SSR without
    * alpha-clipping soft radial glow or multiplying background pixels with alpha == 0.
    */
   public static RenderType irisEmissiveTextured(ResourceLocation texture) {
      return IRIS_EMISSIVE_TEXTURED_CACHE.computeIfAbsent(texture, tex -> create(
         "zol_iris_emissive_tex",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         4096,
         false,
         false,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_EYES_SHADER)
            .setTextureState(new TextureStateShard(tex, false, false))
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)
      ));
   }

   /**
    * Iris Dual-Pass Phase 1 RenderType for untextured vertex-feathered emissive geometry
    * (beams, barrier seams, spirals, impact flashes, singularity accretion rings).
    * Routes through RENDERTYPE_EYES_SHADER (Iris gbuffers_spidereyes) with a pure opaque white
    * 4x4 texture + LIGHTNING_TRANSPARENCY so shaderpacks apply native spidereyes HDR bloom
    * while vertex edges feather smoothly to (0,0,0,0) with zero cutoff disk artifacts.
    */
   public static final RenderType IRIS_EMISSIVE_QUAD = create(
      "zol_iris_emissive_quad",
      DefaultVertexFormat.NEW_ENTITY,
      Mode.QUADS,
      8192,
      false,
      false,
      CompositeState.builder()
         .setShaderState(RENDERTYPE_EYES_SHADER)
         .setTextureState(new TextureStateShard(TEX_WHITE, false, false))
         .setTransparencyState(LIGHTNING_TRANSPARENCY)
         .setLightmapState(LIGHTMAP)
         .setOverlayState(OVERLAY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   /** Flushes a buffered Iris Phase 1 RenderType immediately inside the entity/level pass. */
   public static void endIrisBatch(MultiBufferSource buffers, RenderType type) {
      if (buffers instanceof MultiBufferSource.BufferSource bufferSource) {
         bufferSource.endBatch(type);
      }
   }

   private ZoltraakRenderTypes(String n, VertexFormat f, Mode m, int s, boolean c, boolean o, Runnable a, Runnable b) {
      super(n, f, m, s, c, o, a, b);
   }
}
