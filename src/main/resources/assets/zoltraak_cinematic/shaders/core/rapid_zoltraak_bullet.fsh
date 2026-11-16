#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;

in vec2 uv;
in float age;
in float energy;
in float dark;
in float luminous;

out vec4 fragColor;

void main() {
    float x = clamp(uv.x, 0.0, 1.0);
    float y = abs(uv.y * 2.0 - 1.0);

    // 1. Needle bolt shape (tapered tail, straight shaft, diamond tip)
    float tailFade = smoothstep(0.0, 0.08, x);

    float lanceWidth;
    if (x < 0.08) {
        lanceWidth = mix(0.60, 1.0, smoothstep(0.0, 0.08, x));
    } else if (x < 0.90) {
        lanceWidth = 1.0;
    } else {
        float t = (x - 0.90) / 0.10;
        lanceWidth = (1.0 + 0.12 * sin(t * 3.14159)) * (1.0 - t * 0.95);
    }

    float ripple = 1.0 + 0.04 * sin(x * 65.0 - age * 8.5);
    float effectiveWidth = max(lanceWidth * ripple, 0.005);
    float dist = y / effectiveWidth;

    // 2. Core, inner glow, and outer halo
    float core = exp(-pow(dist * 2.2, 2.5));
    float sheath = exp(-pow(dist * 1.25, 2.0));
    float halo = exp(-pow(dist * 0.70, 1.8)) * 0.45;

    // 3. Tip glint near the front of the bolt
    float glintX = abs(x - 0.96) * 22.0;
    float glintY = y * 7.5;
    float diamondGlint = exp(-glintX - glintY) * 1.5;
    float tipApex = smoothstep(0.88, 1.0, x) * exp(-y * 6.0) * 1.3;

    // 4. Colors (standard cyan vs dark violet)
    if (dark > 0.5) {
        vec3 voidBlack   = vec3(0.003, 0.004, 0.008);
        vec3 sootBlack   = vec3(0.045, 0.038, 0.055);
        vec3 smokeViolet = vec3(0.20, 0.11, 0.32);
        vec3 ashDust     = vec3(0.48, 0.35, 0.65);

        float coreMask = 1.0 - smoothstep(0.48, 0.88, dist);
        float rimMask = smoothstep(0.40, 0.82, dist) * (1.0 - smoothstep(0.95, 1.25, dist));
        float dustNoise = sin(x * 95.0 - age * 11.0) * sin(dist * 25.0);
        float dustSparkle = rimMask * smoothstep(0.2, 0.8, dustNoise);

        vec3 lanceColor = mix(voidBlack, sootBlack, clamp(dist * 0.75, 0.0, 1.0));
        lanceColor = mix(lanceColor, smokeViolet, rimMask * 0.65);
        lanceColor = mix(lanceColor, ashDust, dustSparkle * 0.45);

        // Keep dark core opaque so background does not show through
        float lanceAlpha = clamp((coreMask * 1.0 + rimMask * 0.55) * tailFade * energy * ColorModulator.a, 0.0, 1.0);

        if (lanceAlpha <= 0.003) {
            discard;
        }

        fragColor = vec4(lanceColor * ColorModulator.rgb, lanceAlpha);
        return;
    }

    vec3 violetMana   = vec3(0.48, 0.18, 0.92);
    vec3 cyanElectric = vec3(0.20, 0.88, 1.00);
    vec3 whiteCore    = vec3(1.00, 1.00, 1.00);

    vec3 holyTheme = mix(violetMana, cyanElectric, clamp(sheath * 1.45 - halo * 0.4, 0.0, 1.0));
    holyTheme      = mix(holyTheme, whiteCore, clamp(core * 1.35 + diamondGlint * 0.75 + tipApex * 0.65, 0.0, 1.0));
    holyTheme     += violetMana * halo * 1.35;

    // 5. Final alpha
    float density = (core * 0.98 + sheath * 0.85 + halo * 0.80 + diamondGlint * 0.55 + tipApex * 0.45) * tailFade;
    float alpha = clamp(density * energy * ColorModulator.a, 0.0, 0.95);

    if (alpha <= 0.003) {
        discard;
    }

    fragColor = vec4(holyTheme * ColorModulator.rgb, alpha);
}