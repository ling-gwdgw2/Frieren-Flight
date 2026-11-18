#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.55 * valNoise2(p);
    p = mat2(0.80, -0.60, 0.60, 0.80) * p * 2.04;
    v += 0.30 * valNoise2(p);
    p = mat2(0.80, -0.60, 0.60, 0.80) * p * 2.06;
    v += 0.15 * valNoise2(p);
    return v;
}

vec3 hsv2rgb(float h) {
    vec3 p = abs(fract(vec3(h) + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return clamp(p - 1.0, 0.0, 1.0);
}

vec3 bloomPalette(float t) {
    if (t < 0.20) {
        // 0.08: Magenta-Pink Staff / Purple Shockwave
        return mix(vec3(0.92, 0.18, 0.96), vec3(1.06, 0.50, 1.00), clamp(t / 0.20, 0.0, 1.0));
    } else if (t < 0.38) {
        // 0.26: Electric Cyan-Blue Aura
        return mix(vec3(0.06, 0.52, 0.98), vec3(0.24, 0.78, 1.05), clamp((t - 0.20) / 0.18, 0.0, 1.0));
    } else if (t < 0.60) {
        // 0.48: Fiery Crimson-Red Dome Corona & Ember Haze
        return mix(vec3(0.98, 0.14, 0.02), vec3(1.05, 0.42, 0.05), clamp((t - 0.38) / 0.22, 0.0, 1.0));
    } else if (t < 0.80) {
        // 0.68: Warm Molten Amber-Orange Ground Glow
        return mix(vec3(1.04, 0.46, 0.05), vec3(1.10, 0.78, 0.18), clamp((t - 0.60) / 0.20, 0.0, 1.0));
    } else if (t < 0.90) {
        // 0.84: Rich Sun-Gold Flash
        return vec3(1.10, 0.84, 0.28);
    } else if (t < 0.95) {
        // 0.92: Neon Green Sparkle Bloom
        return vec3(0.24, 0.96, 0.36);
    } else {
        // 0.98: Blinding Cream-White Flash
        return vec3(1.14, 1.08, 0.92);
    }
}

void main() {
    float progress = clamp(vColor.r, 0.0, 1.0);
    float seedVal = vColor.g;
    // mode encoding:
    // 0.00 = ground crimson arcane vortex magic circle
    // 0.50 = stacked sky fire vortex magic ring with crisp geometric framing
    // 0.72 = prismatic rainbow shockwave ring
    // 0.84 = electric cyan-blue fountain ribbon with soft cyan bloom
    // 0.92 = soft-blooming laser beam / radial speed streak
    // 0.98 = pure radial Gaussian bloom / shockwave ring / swirling perimeter gold flame arcs
    float mode = vColor.b;
    float fade = clamp(vColor.a, 0.0, 1.0);
    float anim = GameTime * 4.5 + progress * 7.5 + seedVal * 6.283185;

    vec2 centeredUv = vUv * 2.0 - 1.0;
    float radialDist = length(centeredUv);
    float pxW = clamp(max(fwidth(radialDist), 0.003), 0.003, 0.035);

    // MODE 5: Pure Radial Gaussian Bloom / Shockwave Ring / Swirling Gold Flame Arcs
    if (mode > 0.95) {
        vec2 cp = centeredUv;
        float cr = radialDist;
        if (cr > 1.0) discard;

        vec3 baseTint = bloomPalette(progress);
        float alpha = 0.0;
        vec3 col = baseTint;

        // Soft radial bloom fade toward silhouette
        if (seedVal < 0.34) {
            // Pure soft Gaussian radial bloom glow
            float radialWindow = 1.0 - smoothstep(0.50, 0.96, cr);
            float gCore = exp(-cr * cr * 12.0) * 0.54;
            float gWide = exp(-cr * cr * 5.2) * 0.44;
            float intensity = (gCore + gWide) * radialWindow;
            col = mix(baseTint, vec3(1.10, 1.04, 0.90), clamp(gCore * 0.40, 0.0, 1.0));
            alpha = clamp(intensity * fade * ColorModulator.a, 0.0, 1.0);
        } else if (seedVal < 0.68) {
            // Crisp expanding shockwave / crater ripple ring
            float ringCore = exp(-pow((cr - 0.76) / max(pxW * 1.8, 0.015), 2.0)) * 1.15;
            float ringBloom = exp(-pow((cr - 0.76) * 10.0, 2.0)) * 0.28 * (1.0 - smoothstep(0.86, 1.0, cr));
            float arcMod = 0.85 + 0.22 * valNoise2(vec2(atan(cp.y, cp.x) * 2.0 + anim * 0.5, cr * 4.0));
            col = mix(baseTint, vec3(1.10, 1.02, 0.86), clamp(ringCore * 0.40, 0.0, 1.0));
            alpha = clamp((ringCore + ringBloom) * arcMod * fade * ColorModulator.a, 0.0, 1.0);
        } else {
            // Crisp swirling golden-orange flame ribbons confined strictly to outer dome perimeter
            float angle = atan(cp.y, cp.x);
            vec2 angDir = vec2(cos(angle), sin(angle));
            float wNoise = fbm2(angDir * 3.2 + vec2(anim * 0.38, -anim * 0.44));
            float ringEnv = smoothstep(0.62, 0.72, cr) * (1.0 - smoothstep(0.84, 0.96, cr));
            float arc1 = smoothstep(0.55, 0.92, sin(angle * 2.0 + cr * 5.0 - anim * 1.4 + wNoise * 2.4) * 0.5 + 0.5);
            float arc2 = smoothstep(0.62, 0.96, sin(angle * 3.0 - cr * 7.0 - anim * 1.9 + wNoise * 1.8) * 0.5 + 0.5);
            float crest = exp(-pow((cr - 0.77 + (wNoise - 0.5) * 0.03) * 34.0, 2.0)) * (0.35 + 0.80 * arc1);
            float flame = ringEnv * (arc1 * 0.72 + arc2 * 0.50) + crest;
            col = mix(baseTint, vec3(1.12, 0.90, 0.34), clamp(crest * 0.78 + arc1 * 0.28, 0.0, 1.0));
            alpha = clamp(flame * fade * ColorModulator.a, 0.0, 1.0);
        }

        if (alpha < 0.003) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // MODE 4: Soft-Blooming Laser Pillar & Radial Speed Streaks
    if (mode > 0.89) {
        float u = vUv.x;
        float d = abs(vUv.y * 2.0 - 1.0);
        if (d > 1.0) discard;

        float isStreak = step(0.5, seedVal);
        float lenProfile = mix(1.0, sin(clamp(u, 0.0, 1.0) * 3.14159265) * smoothstep(0.0, 0.22, u), isStreak);

        float whiteCore = exp(-pow(d * 6.5, 2.0));
        float goldSheath = exp(-pow(d * 2.8, 2.0)) * 0.82;
        float softBloom = exp(-pow(d * 1.45, 2.0)) * 0.42 * (1.0 - smoothstep(0.68, 1.0, d));

        vec3 outerBloomCol = mix(vec3(0.98, 0.26, 0.03), vec3(1.00, 0.30, 0.46), progress * 0.50);
        vec3 midGoldCol = vec3(1.04, 0.72, 0.14);
        vec3 hotCoreCol = vec3(1.14, 1.08, 0.88);

        vec3 col = outerBloomCol * softBloom + midGoldCol * goldSheath + hotCoreCol * whiteCore;
        float alpha = clamp((softBloom + goldSheath * 0.82 + whiteCore) * lenProfile * fade * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.003) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // MODE 3: Directional Mana-Gathering Energy Streams rushing INTO the staff orb (u: 0=tail -> 1=head)
    if (mode > 0.78) {
        float u = clamp(vUv.x, 0.0, 1.0);
        float v = abs(vUv.y * 2.0 - 1.0);

        float flow = u * 3.8 - progress * 8.0 + seedVal * 6.283;
        float wave = (valNoise2(vec2(flow * 1.6, v * 2.8)) - 0.5) * 0.12 * (1.0 - u * 0.65);
        float d = clamp(v + wave, 0.0, 1.0);

        float outerBloom = exp(-pow(d * 2.1, 2.0)) * (1.0 - smoothstep(0.68, 1.0, v)) * 0.36;
        float body = 1.0 - smoothstep(0.12, 0.56, d);
        float coreLine = exp(-pow(d * 5.8, 2.0));

        // Directional comet ramp: wispy blue tail at u=0, bright aqua-white plasma head accelerating to u=1
        float tailTaper = smoothstep(0.0, 0.32, u);
        float headCore = smoothstep(0.35, 0.90, u);
        float tipClose = smoothstep(1.0, 0.88, u);
        float streamEnv = tailTaper * tipClose * (0.48 + 0.52 * headCore);

        vec3 deepBlueBloom = vec3(0.02, 0.36, 0.96);
        vec3 electricCyan = vec3(0.12, 0.80, 1.04);
        vec3 brightAquaWhite = vec3(0.76, 0.98, 1.06);

        vec3 col = deepBlueBloom * outerBloom
                 + mix(deepBlueBloom, electricCyan, clamp(body * (0.65 + 0.35 * headCore), 0.0, 1.0)) * 0.86
                 + mix(electricCyan, brightAquaWhite, headCore) * coreLine * (0.58 + 0.42 * headCore);
        float alpha = clamp((outerBloom * 0.55 + body * 0.78 + coreLine * 0.64) * streamEnv * fade * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    vec2 p = centeredUv;
    float r = radialDist;
    if (r > 1.0) discard;

    float angle = atan(p.y, p.x);
    vec3 color = vec3(0.0);
    float alpha = 0.0;
    vec2 angDir = vec2(cos(angle), sin(angle));

    if (mode > 0.62) {
        // MODE 2: Crisp Prismatic Rainbow Shockwave Ring around staff with subtle aura
        float outerRing = exp(-pow((r - 0.76) / max(pxW * 1.7, 0.013), 2.0));
        float innerHair = exp(-pow((r - 0.68) / max(pxW * 1.3, 0.010), 2.0)) * 0.78;
        float softBloom = exp(-pow((r - 0.74) * 9.5, 2.0)) * 0.24 * (1.0 - smoothstep(0.86, 1.0, r));
        float arcNoise = valNoise2(angDir * 3.5 + vec2(anim * 0.8, -anim * 0.6));
        float intensity = (outerRing * 1.25 + innerHair + softBloom) * (0.88 + 0.20 * arcNoise);

        vec3 rainbow = hsv2rgb(fract(angle / 6.2831853 + 0.25)) * 1.04;
        color = mix(rainbow, vec3(1.06, 1.02, 0.96), clamp((outerRing + innerHair) * 0.34, 0.0, 1.0));
        alpha = clamp(intensity * fade * ColorModulator.a, 0.0, 1.0);
    } else if (mode > 0.25) {
        // MODE 1: Razor-sharp concentric sky magic rings + arcane geometry + controlled crimson flame swirls
        float wLine = max(pxW * 1.5, 0.010);
        float outerBezel = exp(-pow((r - 0.86) / wLine, 2.0)) * 1.35;
        float outerInner = exp(-pow((r - 0.79) / wLine, 2.0)) * 1.15;
        float midBezel   = exp(-pow((r - 0.62) / (wLine * 1.1), 2.0)) * 0.78;
        float innerBezel = exp(-pow((r - 0.40) / wLine, 2.0)) * 1.30;
        float innerCore  = exp(-pow((r - 0.34) / wLine, 2.0)) * 0.95;

        // Crisp segmented glyph bezel & 24 radial ticks between outer rings (0.79..0.86)
        float outerBand = smoothstep(0.79, 0.805, r) * (1.0 - smoothstep(0.845, 0.86, r));
        float tickWave = smoothstep(0.32, 0.78, cos(angle * 24.0 + anim * 0.16));
        float glyphBlocks = smoothstep(0.18, 0.52, sin(angle * 8.0 - anim * 0.24));
        float outerRunes = outerBand * (0.55 * tickWave + 0.65 * glyphBlocks);

        // Inner rune ring dashes (0.34..0.40)
        float innerBand = smoothstep(0.34, 0.352, r) * (1.0 - smoothstep(0.388, 0.40, r));
        float innerRunes = innerBand * smoothstep(0.28, 0.76, cos(angle * 16.0 - anim * 0.30)) * 0.85;

        // Rotating 8-point sacred inscribed octagram star chords (exact square max-norm, zero seams!)
        float a1 = anim * 0.12 + seedVal * 3.14159;
        float a2 = a1 + 0.78539816;
        float c1 = cos(a1), s1 = sin(a1);
        float c2 = cos(a2), s2 = sin(a2);
        vec2 q1 = vec2(p.x * c1 - p.y * s1, p.x * s1 + p.y * c1);
        vec2 q2 = vec2(p.x * c2 - p.y * s2, p.x * s2 + p.y * c2);
        float sq1 = max(abs(q1.x), abs(q1.y));
        float sq2 = max(abs(q2.x), abs(q2.y));
        float starWindow = smoothstep(0.38, 0.43, r) * (1.0 - smoothstep(0.75, 0.80, r));
        float starLines = (exp(-pow((sq1 - 0.56) / wLine, 2.0)) + exp(-pow((sq2 - 0.56) / wLine, 2.0))) * starWindow * 0.92;

        // Defined crimson flame tongues swirling inside ring cavity (clean dark sky gaps preserved)
        float swirlEnv = smoothstep(0.34, 0.44, r) * (1.0 - smoothstep(0.78, 0.90, r));
        float swirlNoise = fbm2(angDir * (2.6 + r * 2.2) + vec2(cos(anim * 0.35), sin(anim * 0.35)) * 1.1);
        float arms = sin(angle * 3.0 - r * 8.5 - anim * 1.5 + swirlNoise * 2.6) * 0.5 + 0.5;
        float secondaryArms = sin(angle * 5.0 - r * 11.0 - anim * 2.1 + swirlNoise * 1.8) * 0.5 + 0.5;
        float ribbonMask = smoothstep(0.46, 0.78, arms) * 0.72 + smoothstep(0.58, 0.88, secondaryArms) * 0.38;
        float swirlFlames = swirlEnv * ribbonMask * (0.55 + 0.45 * swirlNoise);

        // Tight subtle crimson aura around the ring frames (eliminates muddy yellow blob!)
        float bloomAura = exp(-pow((r - 0.62) * 5.2, 2.0)) * 0.14 * (1.0 - smoothstep(0.84, 1.0, r));

        float sharpRunes = outerBezel + outerInner + midBezel + innerBezel + innerCore + outerRunes + innerRunes + starLines;
        float totalIntensity = sharpRunes + swirlFlames + bloomAura;

        vec3 crimsonDeep = vec3(0.96, 0.14, 0.02);
        vec3 fieryOrange = vec3(1.04, 0.44, 0.05);
        vec3 crispGold = vec3(1.12, 0.92, 0.34);
        vec3 sharpCore = vec3(1.16, 1.04, 0.72);

        color = mix(crimsonDeep, fieryOrange, smoothstep(0.18, 0.68, swirlFlames + bloomAura));
        color = mix(color, crispGold, clamp(outerRunes + innerRunes + starLines * 0.85 + midBezel * 0.60, 0.0, 1.0));
        color = mix(color, sharpCore, clamp(outerBezel * 0.72 + innerBezel * 0.78 + outerInner * 0.55, 0.0, 1.0));
        alpha = clamp(totalIntensity * fade * ColorModulator.a, 0.0, 1.0);
    } else {
        // MODE 0: Crisp Ground Crimson Arcane Magic Circle + Logarithmic Fire Spirals
        float wLine = max(pxW * 1.5, 0.010);
        float diskMask = 1.0 - smoothstep(0.84, 0.96, r);
        float ambientGroundBloom = exp(-r * r * 3.8) * (1.0 - smoothstep(0.72, 1.0, r)) * 0.18;
        float fNoise = fbm2(angDir * (2.2 + r * 3.6) + vec2(anim * 0.25, -anim * 0.30));

        float gOuter1 = exp(-pow((r - 0.90) / wLine, 2.0)) * 1.38;
        float gOuter2 = exp(-pow((r - 0.84) / wLine, 2.0)) * 1.10;
        float gMid    = exp(-pow((r - 0.54) / wLine, 2.0)) * 1.05;
        float gInner  = exp(-pow((r - 0.28) / wLine, 2.0)) * 0.96;
        float centerHub = exp(-r * r * 24.0) * 0.85;

        // Bezel ticks & rotating 8-point arcane seal inscribed on ground
        float bezelBand = smoothstep(0.84, 0.852, r) * (1.0 - smoothstep(0.888, 0.90, r));
        float gTicks = bezelBand * smoothstep(0.32, 0.80, cos(angle * 24.0 - anim * 0.14)) * 0.90;

        float ga1 = -anim * 0.10;
        float ga2 = ga1 + 0.78539816;
        vec2 gq1 = vec2(p.x * cos(ga1) - p.y * sin(ga1), p.x * sin(ga1) + p.y * cos(ga1));
        vec2 gq2 = vec2(p.x * cos(ga2) - p.y * sin(ga2), p.x * sin(ga2) + p.y * cos(ga2));
        float gStarWin = smoothstep(0.28, 0.33, r) * (1.0 - smoothstep(0.80, 0.85, r));
        float gStar = (exp(-pow((max(abs(gq1.x), abs(gq1.y)) - 0.60) / wLine, 2.0))
                     + exp(-pow((max(abs(gq2.x), abs(gq2.y)) - 0.60) / wLine, 2.0))) * gStarWin * 0.88;

        float spiralArg = angle * 4.0 - log(r + 0.14) * 5.2 - anim * 1.35 + fNoise * 2.0;
        float spiralArm = smoothstep(0.42, 0.82, sin(spiralArg) * 0.5 + 0.5);
        float fireIntensity = (spiralArm * (0.42 + 0.45 * fNoise) * (1.0 - r * 0.30)
                             + gOuter1 + gOuter2 + gMid + gInner + gTicks + gStar + centerHub) * diskMask + ambientGroundBloom;

        vec3 deepCrimson = vec3(0.94, 0.12, 0.02);
        vec3 flameOrange = vec3(1.04, 0.44, 0.04);
        vec3 blazingGold = vec3(1.12, 0.90, 0.30);

        color = mix(deepCrimson, flameOrange, clamp(spiralArm * 0.85 + fNoise * 0.25, 0.0, 1.0));
        color = mix(color, blazingGold, clamp(gOuter1 * 0.82 + gOuter2 * 0.68 + gMid * 0.74 + gInner * 0.70 + gTicks + gStar * 0.78 + centerHub * 0.72, 0.0, 1.0));
        alpha = clamp(fireIntensity * fade * ColorModulator.a, 0.0, 1.0);
    }

    if (alpha < 0.004) discard;
    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
