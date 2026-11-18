#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vColor;

out vec4 fragColor;

// Helper: rotate 2D vector by angle
vec2 rotateVec(vec2 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

// Sample texture with subtle RGB fringe
vec4 samplePrismatic(sampler2D tex, vec2 p_rot, vec2 dir, float dispersion) {
    vec2 uv_center = (p_rot + 1.0) * 0.5;
    vec2 uv_r = uv_center - dir * dispersion;
    vec2 uv_g = uv_center;
    vec2 uv_b = uv_center + dir * dispersion;

    float r_ch = texture(tex, clamp(uv_r, 0.0, 1.0)).r;
    float g_ch = texture(tex, clamp(uv_g, 0.0, 1.0)).g;
    float b_ch = texture(tex, clamp(uv_b, 0.0, 1.0)).b;
    float a_ch = texture(tex, clamp(uv_g, 0.0, 1.0)).a;

    return vec4(r_ch, g_ch, b_ch, a_ch);
}

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.015) discard;

    vec2 dir = (r > 0.001) ? (p / r) : vec2(0.0);
    float t = GameTime * 1200.0;

    // Rotation comes from CPU vertex positions (keeps Iris pass synced)
    vec2 p_rot = p;

    // Clockwise sweep-in animation
    float formation = clamp(vColor.r, 0.0, 1.0);
    float drawMask = 1.0;
    float leadSpark = 0.0;

    if (formation < 0.999) {
        float sweepAngle = fract((atan(-p_rot.x, p_rot.y) + 3.14159265) / 6.2831853);
        float ringMask = smoothstep(sweepAngle - 0.02, sweepAngle, formation);
        float petalBloom = smoothstep(0.15, 0.90, formation);
        float petalRadiusMask = smoothstep(r / 0.62, (r / 0.62) - 0.08, petalBloom);
        drawMask = mix(petalRadiusMask, ringMask, smoothstep(0.55, 0.65, r));

        float headDiff = abs(sweepAngle - formation);
        leadSpark = exp(-headDiff * headDiff * 1600.0) * smoothstep(0.60, 0.98, r) * 3.5;
    }

    if (drawMask < 0.005 && leadSpark < 0.01) discard;

    // Sample crisp rune texture directly for sharpness, keeping RGB dispersion ultra-tight
    vec4 texCenter = texture(Sampler0, clamp(uv, 0.0, 1.0));
    vec4 tex = samplePrismatic(Sampler0, p_rot, dir, 0.0008);
    float rawLuma = max(texCenter.r, max(texCenter.g, texCenter.b)) * texCenter.a;
    float runeLuma = clamp(rawLuma * 0.82 + smoothstep(0.08, 0.45, rawLuma) * 0.34, 0.0, 1.0);

    // Outward pulse wave
    float wave = exp(-pow(fract(r * 2.2 - t * 0.70) * 4.0, 2.0)) * 0.28;

    // Gentle shimmer
    float shimmer = 0.94 + 0.06 * sin(t * 3.2);
    float runeIntensity = (runeLuma * drawMask) * (1.0 + wave) * shimmer;

    // Optional first-person center fade / edge-only mask passed via vColor.gb (1.0, 1.0 = full external view)
    float centerKeep = clamp(vColor.g, 0.0, 1.0);
    float innerCut = vColor.b < 0.96 ? smoothstep(0.56, 0.76, r) : 1.0;
    float radialCenterFade = mix(centerKeep, 1.0, smoothstep(0.16, 0.74, r));
    float firstPersonMask = radialCenterFade * innerCut;

    // Center glow (faded around crosshair in first-person)
    float coreGlow = exp(-r * r * 42.0) * 0.35 * runeLuma * centerKeep * (vColor.b < 0.96 ? 0.0 : 1.0);

    // Dark violet palette
    vec3 darkPurple     = vec3(0.28, 0.10, 0.44);
    vec3 royalViolet    = vec3(0.58, 0.24, 0.86);
    vec3 silverLavender = vec3(0.88, 0.82, 0.98);

    vec3 runeColor = mix(darkPurple, royalViolet, smoothstep(0.10, 0.55, runeLuma));
    runeColor = mix(runeColor, silverLavender, smoothstep(0.55, 0.92, runeLuma));

    vec3 finalColor = runeColor * runeIntensity;
    finalColor += silverLavender * (coreGlow + leadSpark);

    // Smooth outer edge
    float circleCut = 1.0 - smoothstep(0.985, 1.008, r);

    // Final alpha
    float totalAlpha = ((runeLuma * drawMask) * 0.98 + coreGlow + leadSpark * 0.9)
                       * firstPersonMask * circleCut * vColor.a * ColorModulator.a;
    if (totalAlpha < 0.008) discard;

    fragColor = vec4(finalColor * ColorModulator.rgb, clamp(totalAlpha, 0.0, 1.0));
}
