#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = rot * p * 2.02;
        a *= 0.5;
    }
    return v;
}

float ridged(vec2 p) {
    float n1 = 1.0 - abs(noise(p) * 2.0 - 1.0);
    float n2 = 1.0 - abs(noise(p * 2.15 + vec2(5.2, 3.7)) * 2.0 - 1.0);
    return n1 * 0.60 + n2 * 0.40;
}

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;

    float progress = clamp(vertexColor.r, 0.0, 1.0);
    bool isBlack = vertexColor.g > 0.5;
    bool isSurface = vertexColor.b > 0.5;
    float fade = clamp(vertexColor.a, 0.0, 1.0);

    float texAlpha = texture(Sampler0, uv).a;
    float timeAnim = progress * 4.0 + GameTime * 0.35;
    float viewDist = length(viewPosition);

    vec3 finalColor = vec3(0.0);
    float finalAlpha = 0.0;

    if (isSurface) {
        // MODE 1: Organic crater fracture scorch, superheated mana fissures & dual supersonic shock rings
        float angle = atan(p.y, p.x);
        vec2 polarWarp = vec2(cos(angle), sin(angle)) * (2.6 + r * 3.8);
        float fNoise = fbm(polarWarp + vec2(timeAnim * 0.12));

        // Dual supersonic shockwave rings racing outward cleanly without harsh square edges
        float wavePos1 = 0.20 + progress * 0.72;
        float waveRing1 = exp(-pow((r - wavePos1 + (fNoise - 0.5) * 0.07) * 14.0, 2.0));
        float wavePos2 = 0.10 + progress * 0.54;
        float waveRing2 = exp(-pow((r - wavePos2 + (fNoise - 0.5) * 0.05) * 19.0, 2.0)) * 0.75;

        // Asymmetric branched Voronoi-like crack filaments radiating out from scorched crater hub
        vec2 crackCoord = p * (3.8 + r * 2.2) + vec2(cos(angle * 3.0), sin(angle * 5.0)) * 0.25;
        float branch = pow(ridged(crackCoord + vec2(fNoise * 1.2)), 4.2);
        float innerScorch = smoothstep(0.62, 0.12, r + (fNoise - 0.5) * 0.14);
        float fissureMask = branch * smoothstep(0.88, 0.10, r) * smoothstep(0.04, 0.14, r);
        float craterGlow = exp(-r * r * 12.0) * (1.2 - progress * 0.75);

        float edgeFade = 1.0 - smoothstep(0.76, 1.0, r);

        if (isBlack) {
            vec3 scorchVoid = vec3(0.015, 0.008, 0.032);
            vec3 neonViolet = vec3(0.64, 0.16, 0.98);
            vec3 shockMagenta = vec3(0.84, 0.38, 1.00);
            vec3 craterWhite = vec3(0.96, 0.84, 1.00);

            finalColor = mix(scorchVoid, neonViolet, clamp(fissureMask * 1.45 + waveRing2 * 0.7, 0.0, 1.0));
            finalColor = mix(finalColor, shockMagenta, clamp(waveRing1 * 0.95, 0.0, 1.0));
            finalColor = mix(finalColor, craterWhite, clamp(craterGlow * 1.15 + fissureMask * waveRing2, 0.0, 1.0));
            finalAlpha = max(innerScorch * 0.85, max(waveRing1 * 0.88 + waveRing2 * 0.65, fissureMask * 0.92 + craterGlow * 0.85)) * edgeFade * fade;
        } else {
            vec3 cobaltBurn = vec3(0.08, 0.32, 0.88);
            vec3 manaCyan = vec3(0.24, 0.86, 1.00);
            vec3 whitePlasma = vec3(0.96, 0.99, 1.00);

            finalColor = mix(cobaltBurn, manaCyan, clamp(waveRing1 * 0.85 + fissureMask * 1.15, 0.0, 1.0));
            finalColor = mix(finalColor, whitePlasma, clamp(craterGlow * 1.15 + waveRing2 * 0.85 + fissureMask * 0.70, 0.0, 1.0));
            finalAlpha = (craterGlow * 0.95 + waveRing1 * 0.90 + waveRing2 * 0.70 + fissureMask * 0.82 + innerScorch * 0.35) * edgeFade * fade;
        }
    } else {
        // MODE 2: Turbulent plasma cloud burst (kept refined for Rapid / Barrage small bolts, zero square pixels)
        float coreFlash = exp(-r * r * 5.2) * exp(-progress * 7.5) * 3.0;
        vec2 warpCoord = p * 2.8 + vec2(fbm(p * 1.9 + timeAnim * 0.4)) * 0.9;
        float billow = fbm(warpCoord - normalize(p + vec2(0.0001)) * progress * 1.6);

        float smokeRadius = 0.36 + 0.54 * pow(progress, 0.45);
        float smokeShape = smoothstep(smokeRadius, smokeRadius - 0.36, r - (billow - 0.45) * 0.28);
        float edgeMask = 1.0 - smoothstep(0.74, 0.98, r);
        float swirlRim = smoothstep(0.35, 0.92, 1.0 - billow) * smokeShape * (1.0 - progress * 0.70);

        // Smooth circular sparkle micro-embers without 10x10 square cell artifacts
        vec2 sparkUv = p * 7.5 - normalize(p + vec2(0.0001)) * (progress * 3.8);
        vec2 sparkCell = floor(sparkUv);
        vec2 sparkFrac = fract(sparkUv) - 0.5;
        vec2 sparkJitter = vec2(hash12(sparkCell), hash12(sparkCell + vec2(17.1, 31.7))) - 0.5;
        float sparkDist = length(sparkFrac - sparkJitter * 0.55);
        float embers = smoothstep(0.16, 0.02, sparkDist) * step(0.72, hash12(sparkCell + 4.2)) * smoothstep(0.88, 0.25, r) * (1.0 - progress);

        if (isBlack) {
            vec3 sootCol = vec3(0.015, 0.008, 0.028);
            vec3 neonPurple = vec3(0.55, 0.14, 0.92);
            vec3 brightViolet = vec3(0.88, 0.58, 1.00);

            vec3 soot = mix(sootCol, neonPurple, swirlRim * 0.65);
            finalColor = mix(soot, brightViolet, clamp(coreFlash * 0.55 + embers, 0.0, 1.0));
            finalAlpha = max(smokeShape * 0.85, coreFlash * 0.72 + embers * 0.85) * edgeMask * fade;
        } else {
            vec3 deepMana = vec3(0.10, 0.36, 0.86);
            vec3 manaCyan = vec3(0.28, 0.88, 1.00);
            vec3 whiteCore = vec3(0.96, 0.99, 1.00);

            vec3 cloudCol = mix(deepMana, manaCyan, swirlRim * 0.85 + 0.15);
            finalColor = mix(cloudCol, whiteCore, clamp(coreFlash * 0.70 + embers * 0.90, 0.0, 1.0));
            finalAlpha = (smokeShape * 0.65 + coreFlash * 0.88 + embers * 0.92) * edgeMask * fade;
        }
    }

    float distFade = mix(0.95, 1.0, clamp(viewDist * 0.15, 0.0, 1.0));
    finalAlpha *= mix(0.85, 1.0, texAlpha) * distFade * ColorModulator.a;
    if (finalAlpha < 0.004) discard;

    fragColor = vec4(finalColor * ColorModulator.rgb, clamp(finalAlpha, 0.0, 1.0));
}
