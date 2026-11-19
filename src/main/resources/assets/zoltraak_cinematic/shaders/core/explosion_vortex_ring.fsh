#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.28318530;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

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

float ridged2(vec2 p) {
    float n1 = 1.0 - abs(valNoise2(p) * 2.0 - 1.0);
    p = mat2(0.80, -0.60, 0.60, 0.80) * p * 2.12 + vec2(4.7, 2.3);
    float n2 = 1.0 - abs(valNoise2(p) * 2.0 - 1.0);
    p = mat2(0.80, -0.60, 0.60, 0.80) * p * 2.08 + vec2(9.1, 6.4);
    float n3 = 1.0 - abs(valNoise2(p) * 2.0 - 1.0);
    return n1 * 0.55 + n2 * 0.30 + n3 * 0.15;
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0e-5), 0.0, 1.0);
    return length(pa - ba * h);
}

// Crisp procedural anime rune glyph inside a local cell [-1, 1]^2
float arcaneRuneGlyph(vec2 cellUv, float glyphId, float pxW) {
    float h1 = hash11(glyphId * 13.7 + 1.3);
    float h2 = hash11(glyphId * 29.1 + 7.9);
    float h3 = hash11(glyphId * 47.3 + 3.1);

    float d = 1.0;
    // Central spine or chevron
    if (h1 > 0.32) {
        d = min(d, sdSegment(cellUv, vec2(0.0, -0.62), vec2(0.0, 0.62)));
    } else {
        d = min(d, sdSegment(cellUv, vec2(-0.46, -0.45), vec2(0.0, 0.55)));
        d = min(d, sdSegment(cellUv, vec2(0.46, -0.45), vec2(0.0, 0.55)));
    }
    // Crossbar / diagonal rune stroke
    if (h2 > 0.55) {
        d = min(d, sdSegment(cellUv, vec2(-0.48, 0.18), vec2(0.48, -0.22)));
    } else if (h2 > 0.25) {
        d = min(d, sdSegment(cellUv, vec2(-0.45, 0.0), vec2(0.45, 0.0)));
        d = min(d, sdSegment(cellUv, vec2(0.0, 0.0), vec2(0.42, 0.52)));
    } else {
        d = min(d, abs(length(cellUv) - 0.36));
    }
    // Accent serif or sacred dot
    if (h3 > 0.52) {
        d = min(d, sdSegment(cellUv, vec2(-0.38, 0.56), vec2(0.38, 0.56)));
    } else if (h3 > 0.22) {
        d = min(d, length(cellUv - vec2(0.0, -0.52)) - 0.11);
    }
    float strokeW = max(pxW * 6.5, 0.10);
    return smoothstep(strokeW + 0.08, max(0.02, strokeW - 0.05), d);
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
    // 0.00 = ground crimson arcane vortex magic circle (Polar SDF runes + log-polar spiral fire)
    // 0.50 = stacked sky fire vortex magic ring (SDF runes + log-polar flame tongues)
    // 0.72 = prismatic rainbow shockwave ring
    // 0.84 = directional cyan-blue mana absorption stream
    // 0.92 = roaring sheared plasma pillar (seed <= 0.38) / jagged anime lightning arc (0.38..0.72) / speed streak (> 0.72)
    // 0.98 = Gaussian bloom (seed < 0.28) / shock ring (0.28..0.58) / molten crater fractures (0.58..0.78) / flame arcs (>= 0.78)
    float mode = vColor.b;
    float fade = clamp(vColor.a, 0.0, 1.0);
    // GameTime wraps 0..1 per 24000 ticks (1200s); convert to bounded periodic seconds for uniform motion
    float sec = mod(GameTime * 1200.0, 240.0);
    float anim = sec * 1.45 + seedVal * TAU;

    vec2 centeredUv = vUv * 2.0 - 1.0;
    float radialDist = length(centeredUv);
    vec2 uvDeriv = vec2(length(vec2(dFdx(vUv.x), dFdy(vUv.x))), length(vec2(dFdx(vUv.y), dFdy(vUv.y))));
    float pxW = clamp(max(fwidth(radialDist), max(uvDeriv.x, uvDeriv.y) * 1.35), 0.0025, 0.028);

    // MODE 5: Radial Gaussian Bloom / Shockwave Ring / Molten Crater Fracture Decal / Swirling Flame Arcs
    if (mode > 0.95) {
        vec2 cp = centeredUv;
        float cr = radialDist;
        if (cr > 1.0) discard;

        vec3 baseTint = bloomPalette(progress);
        float alpha = 0.0;
        vec3 col = baseTint;

        if (seedVal < 0.28) {
            // Pure soft Gaussian radial bloom glow
            float radialWindow = 1.0 - smoothstep(0.50, 0.96, cr);
            float gCore = exp(-cr * cr * 12.0) * 0.54;
            float gWide = exp(-cr * cr * 5.2) * 0.44;
            float intensity = (gCore + gWide) * radialWindow;
            col = mix(baseTint, vec3(1.10, 1.04, 0.90), clamp(gCore * 0.40, 0.0, 1.0));
            alpha = clamp(intensity * fade * ColorModulator.a, 0.0, 1.0);
        } else if (seedVal < 0.58) {
            // Crisp expanding shockwave / crater ripple ring
            float ringCore = exp(-pow((cr - 0.76) / max(pxW * 1.8, 0.015), 2.0)) * 1.15;
            float ringBloom = exp(-pow((cr - 0.76) * 10.0, 2.0)) * 0.28 * (1.0 - smoothstep(0.86, 1.0, cr));
            float arcMod = 0.85 + 0.22 * valNoise2(vec2(atan(cp.y, cp.x) * 2.0 + anim * 0.5, cr * 4.0));
            col = mix(baseTint, vec3(1.10, 1.02, 0.86), clamp(ringCore * 0.40, 0.0, 1.0));
            alpha = clamp((ringCore + ringBloom) * arcMod * fade * ColorModulator.a, 0.0, 1.0);
        } else if (seedVal < 0.78) {
            // Molten Crater Fracture & Superheated Magma Fissure Ground Decal
            float ang = atan(cp.y, cp.x);
            vec2 dir = vec2(cos(ang), sin(ang));
            float nWarp = fbm2(dir * (2.8 + cr * 4.0) + vec2(progress * 0.6));
            vec2 crackP = cp * (4.2 + cr * 2.6) + vec2(cos(ang * 3.0), sin(ang * 5.0)) * 0.24;
            float crackPrimary = pow(ridged2(crackP + vec2(nWarp * 1.15)), 4.6);
            float crackFine = pow(ridged2(crackP * 2.15 - vec2(nWarp * 0.85)), 5.2) * 0.72;
            float fissureMask = (crackPrimary + crackFine) * smoothstep(0.94, 0.14, cr) * smoothstep(0.03, 0.12, cr);
            float craterCore = exp(-cr * cr * 9.5) * (1.25 - progress * 0.68);
            float emberPulse = 0.82 + 0.18 * sin(sec * 3.5 + nWarp * 9.0);
            float edgeFade = 1.0 - smoothstep(0.76, 1.0, cr);

            vec3 deepLava = vec3(0.95, 0.16, 0.02);
            vec3 moltenOrange = vec3(1.10, 0.54, 0.06);
            vec3 whiteMagma = vec3(1.22, 1.06, 0.68);

            col = mix(deepLava, moltenOrange, clamp(fissureMask * 1.15, 0.0, 1.0));
            col = mix(col, whiteMagma, clamp(craterCore * 0.85 + pow(fissureMask, 1.6) * 0.75 * (1.0 - progress * 0.55), 0.0, 1.0));
            alpha = clamp((fissureMask * 1.15 * emberPulse + craterCore * 0.72) * edgeFade * fade * ColorModulator.a, 0.0, 1.0);
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

    // MODE 4: Roaring Anime Plasma Pillar (seed <= 0.38) / Jagged Lightning Arc (0.38..0.72) / Speed Streak (> 0.72)
    if (mode > 0.89) {
        float u = clamp(vUv.x, 0.0, 1.0);
        float sv = vUv.y * 2.0 - 1.0;
        float d = abs(sv);
        if (d > 1.0) discard;

        // 4a. Jagged Anime Sakuga Lightning / Mana Discharge Arc (0.38 < seedVal <= 0.72)
        if (seedVal > 0.38 && seedVal <= 0.72) {
            float stepT = floor(sec * 20.0 + seedVal * 31.0);
            float endPin = sin(u * PI);
            float j1 = (valNoise2(vec2(u * 8.5 + stepT * 1.73, seedVal * 19.0)) - 0.5) * 0.88;
            float j2 = (valNoise2(vec2(u * 22.0 - stepT * 2.41, seedVal * 43.0)) - 0.5) * 0.34;
            float boltCenter = (j1 + j2) * endPin;
            float distBolt = abs(sv - boltCenter);

            // Secondary thinner forked sub-arc
            float subCenter = boltCenter + (valNoise2(vec2(u * 14.0 + stepT * 3.1, seedVal * 71.0)) - 0.5) * 0.58 * endPin;
            float distSub = abs(sv - subCenter);

            float boltCore = exp(-pow(distBolt * 14.0, 2.0));
            float subCore = exp(-pow(distSub * 22.0, 2.0)) * 0.52 * smoothstep(0.18, 0.55, u) * smoothstep(0.95, 0.55, u);
            float boltHalo = exp(-pow(distBolt * 3.8, 2.0)) * 0.48 * (1.0 - smoothstep(0.65, 1.0, d));
            float flicker = 0.72 + 0.28 * hash11(stepT + seedVal * 17.0);
            float lenEnv = smoothstep(0.0, 0.08, u) * smoothstep(1.0, 0.92, u);

            vec3 arcCrimson = mix(vec3(1.02, 0.28, 0.05), vec3(1.00, 0.35, 0.82), progress * 0.65);
            vec3 arcGold = vec3(1.14, 0.86, 0.30);
            vec3 arcWhite = vec3(1.25, 1.18, 1.02);

            vec3 col = arcCrimson * boltHalo + arcGold * (boltCore * 0.65 + subCore) + arcWhite * pow(boltCore, 1.8);
            float alpha = clamp((boltCore * 1.15 + subCore + boltHalo) * lenEnv * flicker * fade * ColorModulator.a, 0.0, 1.0);
            if (alpha < 0.004) discard;
            fragColor = vec4(col * ColorModulator.rgb, alpha);
            return;
        }

        // 4b. Radial Speed Streak / Horizontal Flare Slash (seedVal > 0.72)
        if (seedVal > 0.72) {
            float lenProfile = sin(u * PI) * smoothstep(0.0, 0.22, u);
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

        // 4c. Roaring Anime Plasma Pillar (seedVal <= 0.38): sheared bristles + helical filaments + violent ridged flow
        float flow = u * 11.0 - sec * 18.0;
        float ridgeFlow = ridged2(vec2(flow, sv * 3.8 + u * 2.2));
        float fineFibers = valNoise2(vec2(u * 26.0 - sec * 28.0, sv * 14.0));
        float wisp = ridgeFlow * 0.65 + fineFibers * 0.35;

        // Pulsing jagged plasma core + sheared fiery outer bristles
        float coreWarp = (wisp - 0.5) * 0.11 * smoothstep(0.06, 0.34, d);
        float dCore = max(0.0, d - coreWarp);
        float dBristle = max(0.0, d - (wisp - 0.46) * 0.22 * smoothstep(0.14, 0.52, d));

        float whiteCore = exp(-pow(dCore * 5.8, 2.0));
        float goldSheath = exp(-pow(dCore * 2.7, 2.0)) * (0.76 + 0.36 * ridgeFlow);
        float bristles = pow(clamp(wisp * 1.15, 0.0, 1.0), 2.2) * smoothstep(0.16, 0.34, d) * exp(-max(0.0, d - 0.26) * 3.6);
        float softBloom = exp(-pow(dBristle * 1.48, 2.0)) * 0.46 * (1.0 - smoothstep(0.68, 1.0, d));

        // Twin counter-twisting helical energy strands racing down the vertical sky column
        float helixY1 = sin(u * 18.0 - sec * 16.0) * 0.36;
        float helixY2 = sin(u * 18.0 - sec * 16.0 + PI) * 0.36;
        float helixArc = (exp(-pow((sv - helixY1) * 14.0, 2.0)) + exp(-pow((sv - helixY2) * 14.0, 2.0))) * 0.44 * (0.65 + 0.35 * ridgeFlow);

        vec3 outerBloomCol = mix(vec3(1.00, 0.22, 0.03), vec3(1.02, 0.30, 0.50), progress * 0.52);
        vec3 midGoldCol = vec3(1.08, 0.74, 0.14);
        vec3 hotCoreCol = vec3(1.20, 1.14, 0.94);

        vec3 col = outerBloomCol * (softBloom + bristles * 0.75)
                 + midGoldCol * (goldSheath + bristles * 0.45 + helixArc * 0.65)
                 + hotCoreCol * (whiteCore + helixArc * 0.55);
        float alpha = clamp((softBloom + bristles * 0.60 + goldSheath * 0.84 + whiteCore + helixArc * 0.62) * fade * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.003) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // MODE 3: Directional Mana-Gathering Energy Streams rushing INTO the staff orb (u: 0=tail -> 1=head)
    if (mode > 0.78) {
        float u = clamp(vUv.x, 0.0, 1.0);
        float v = abs(vUv.y * 2.0 - 1.0);

        float flow = u * 3.8 - progress * 8.0 + seedVal * TAU;
        float wave = (valNoise2(vec2(flow * 1.6, v * 2.8)) - 0.5) * 0.12 * (1.0 - u * 0.65);
        float d = clamp(v + wave, 0.0, 1.0);

        float outerBloom = exp(-pow(d * 2.1, 2.0)) * (1.0 - smoothstep(0.68, 1.0, v)) * 0.36;
        float body = 1.0 - smoothstep(0.12, 0.56, d);
        float coreLine = exp(-pow(d * 5.8, 2.0));

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

        vec3 rainbow = hsv2rgb(fract(angle / TAU + 0.25)) * 1.04;
        color = mix(rainbow, vec3(1.06, 1.02, 0.96), clamp((outerRing + innerHair) * 0.34, 0.0, 1.0));
        alpha = clamp(intensity * fade * ColorModulator.a, 0.0, 1.0);
    } else if (mode > 0.25) {
        // MODE 1: Anime Sky Explosion Circle — Fiery Blazing Rings, Sweeping Pinwheel Flame Ribbons & Full Sacred Geometry
        float rot = anim * 0.26 + seedVal * TAU;
        float aRot = angle - rot;
        float rOuter = 0.82;
        float rInner = 0.285;
        float wHair = max(pxW * 1.05, 0.0052);
        float wFire = max(pxW * 2.05, 0.013);

        // Bounded rotated object-space coordinates: length(q) <= 1.0 at ALL GameTime values (zero aliasing!)
        float crRot = cos(rot), srRot = sin(rot);
        vec2 q = vec2(p.x * crRot + p.y * srRot, -p.x * srRot + p.y * crRot);
        vec2 drift1 = vec2(mod(sec * 0.55, 128.0), -mod(sec * 0.35, 128.0));
        vec2 drift2 = vec2(-mod(sec * 0.42, 128.0), mod(sec * 0.62, 128.0));
        // Ultra-smooth 1-octave flow for curve deformation + soft fbm texture strictly for ember shading
        float curveWave = valNoise2(q * 3.0 + drift1);
        float curveWave2 = valNoise2(q * 4.2 + drift2 + vec2(seedVal * 9.0));
        float fNoise1 = fbm2(q * 3.4 + drift1);
        float fNoise2 = fbm2(q * 5.2 + drift2);
        float flameTex = clamp(fNoise1 * 0.62 + fNoise2 * 0.38, 0.0, 1.0);

        // 1. Rich translucent crimson vortex backdrop & inner convergence radial spokes
        float diskGlow = exp(-pow(max(0.0, r - 0.10) / 0.68, 2.0)) * 0.30 * (1.0 - smoothstep(0.84, 0.99, r));
        float radialRay = pow(clamp(0.5 + 0.5 * cos(aRot * 12.0 + (curveWave - 0.5) * 0.65), 0.0, 1.0), 6.0);
        float insideMask = 1.0 - smoothstep(rInner * 0.84, rInner * 1.02, r);
        float centerRays = (radialRay * 0.52 + 0.14) * insideMask * (0.35 + 0.65 * (1.0 - r / max(rInner, 0.05)));
        float centerPin = exp(-r * r * 140.0) * 0.95;

        // 2. Fierce Blazing Inner Core Ring (r ≈ 0.285) with silky golden-white ring & licking crimson corona
        float innerWarp = (curveWave - 0.5) * 0.014 + (curveWave2 - 0.5) * 0.008;
        float dInRing = abs(r - (rInner + innerWarp));
        float innerCore = exp(-pow(dInRing / (wFire * 1.25), 2.0)) * 1.65;
        float innerCorona = exp(-pow((r - rInner) / 0.055, 2.0)) * (0.72 + 0.80 * flameTex);

        // 3. Blazing Outer Fire Ring (r ≈ 0.82) + subtle perimeter rings + whipping tangential flame horns
        float outerWarp = (curveWave - 0.5) * 0.015 + sin(aRot * 7.0) * 0.004;
        float dOutRing = abs(r - (rOuter + outerWarp));
        float outerCore = exp(-pow(dOutRing / (wFire * 1.15), 2.0)) * 1.50;
        float outerCorona = exp(-pow((r - rOuter) / 0.062, 2.0)) * (0.64 + 0.85 * flameTex) * (1.0 - smoothstep(0.86, 0.995, r));
        float outerBezel = exp(-pow((r - 0.852) / wHair, 2.0)) * 0.48;
        float innerBezel = exp(-pow((r - 0.785) / wHair, 2.0)) * 0.44;

        // Smooth curved tangential flame horns peeling off outside the rim (0.78 -> 0.99)
        float nOuterWhips = 7.0;
        float whipPhase = (aRot - 2.35 * log(max(r / rOuter, 0.65)) + (curveWave - 0.5) * 0.08) * nOuterWhips;
        float dWhip = abs(fract(whipPhase / TAU + 0.5) - 0.5) * (TAU / nOuterWhips) * r;
        float whipSpan = smoothstep(0.76, 0.83, r) * (1.0 - smoothstep(0.88, 0.99, r));
        float whipW = mix(max(0.030, pxW * 2.2), max(0.006, pxW * 0.9), clamp((r - 0.80) / 0.19, 0.0, 1.0));
        float outerWhips = exp(-pow(dWhip / whipW, 2.0)) * whipSpan * (0.78 + 0.58 * flameTex);

        // 4. Smooth, Silky Sweeping Pinwheel Flame Tendrils (6 tapered curved flame ribbons + broad ember glow wake)
        float bandEnv = smoothstep(rInner - 0.015, rInner + 0.055, r) * (1.0 - smoothstep(rOuter - 0.02, rOuter + 0.07, r));
        float rSpanNorm = clamp((r - rInner) / (rOuter - rInner), 0.0, 1.0);
        float nArms = 6.0;
        float smoothWarp = (curveWave - 0.5) * 0.075 + sin(rSpanNorm * PI * 2.0) * 0.03;
        float armPhase = (aRot - 2.05 * log(max(r / rInner, 0.35)) + smoothWarp) * nArms;
        float dArm = abs(fract(armPhase / TAU + 0.5) - 0.5) * (TAU / nArms) * r;
        float armWidth = max(0.015 + 0.024 * sin(rSpanNorm * PI), pxW * 2.4);
        float whipRibbon = (exp(-pow(dArm / (armWidth * 0.42), 2.0)) * 1.10
                         + exp(-pow(dArm / (armWidth * 1.75), 2.0)) * (0.52 + 0.68 * flameTex)) * bandEnv;

        // Delicate secondary swirling golden filament wisps
        float subPhase = (aRot - 1.35 * log(max(r / rInner, 0.35)) - 0.42 + (curveWave2 - 0.5) * 0.05) * nArms;
        float dSub = abs(fract(subPhase / TAU + 0.5) - 0.5) * (TAU / nArms) * r;
        float subWisp = exp(-pow(dSub / max(0.011, pxW * 1.4), 2.0)) * 0.58 * (0.65 + 0.45 * flameTex) * bandEnv;

        float spiralRibbons = whipRibbon + subWisp;

        // 5. Complete Seamless Sacred Geometry (Tips reach rOuter cleanly + Full Unclipped Intersecting Orbital Circles)
        float geomWindow = smoothstep(rInner, rInner + 0.025, r) * (1.0 - smoothstep(rOuter - 0.015, rOuter + 0.015, r));
        float sec6 = TAU / 6.0;
        float k6 = mod(aRot + sec6 * 0.5, sec6) - sec6 * 0.5;
        float k6Off = mod(aRot + sec6, sec6) - sec6 * 0.5;
        // Hexagram chords: 0.70 / cos(30 deg) = 0.808 -> vertices kiss the blazing outer ring!
        float chordHex1 = exp(-pow(abs(r * cos(k6) - 0.70) / wHair, 2.0)) * 0.68;
        float chordHex2 = exp(-pow(abs(r * cos(k6Off) - 0.54) / wHair, 2.0)) * 0.54;

        // 3 seamless full intersecting large eccentric rings (evaluated globally, zero sector seam cutoffs!)
        float orbCircles = 0.0;
        for (int ci = 0; ci < 3; ci++) {
            float ca = rot + float(ci) * (TAU / 3.0);
            vec2 cOff = vec2(cos(ca), sin(ca)) * 0.23;
            float dRing = abs(length(p - cOff) - 0.575);
            orbCircles += exp(-pow(dRing / wHair, 2.0)) * 0.52;
        }
        float midRing = exp(-pow((r - 0.56) / wHair, 2.0)) * 0.52;
        float crispGeometry = (chordHex1 + chordHex2 + min(orbCircles, 0.95) + midRing) * geomWindow;

        float fireIntensity = diskGlow + centerRays + centerPin + innerCore + innerCorona + outerCore + outerCorona
                            + outerBezel + innerBezel + outerWhips + spiralRibbons + crispGeometry;
        float hotWeight = clamp(innerCore * 0.76 + outerCore * 0.68 + whipRibbon * 0.44 + crispGeometry * 0.50 + centerPin * 0.75 + centerRays * 0.55, 0.0, 1.0);
        float whiteWeight = clamp(innerCore * 0.48 + outerCore * 0.36 + centerPin * 0.60 + exp(-pow(dArm / max(0.008, pxW * 1.1), 2.0)) * bandEnv * 0.40, 0.0, 1.0);

        vec3 crimsonVelvet = vec3(0.90, 0.10, 0.02);
        vec3 fieryOrange   = vec3(1.10, 0.40, 0.03);
        vec3 lemonGold     = vec3(1.20, 0.90, 0.22);
        vec3 whiteFlame    = vec3(1.30, 1.18, 0.78);

        color = mix(crimsonVelvet, fieryOrange, clamp(outerCorona * 0.72 + innerCorona * 0.72 + spiralRibbons * 0.76 + outerWhips * 0.82 + centerRays * 0.70, 0.0, 1.0));
        color = mix(color, lemonGold, hotWeight);
        color = mix(color, whiteFlame, whiteWeight);
        alpha = clamp(fireIntensity * fade * ColorModulator.a, 0.0, 1.0);
    } else {
        // MODE 0: Ground Crimson Arcane Vortex Seal — Seamless Sacred Circles, Star Chords & Smooth Fire Spirals
        float rot = -anim * 0.24 + seedVal * TAU;
        float aRot = angle - rot;
        float rOuter = 0.84;
        float rInner = 0.24;
        float wHair = max(pxW * 1.05, 0.0052);
        float wFire = max(pxW * 2.05, 0.013);

        // Bounded rotated object-space coordinates: zero high-frequency GameTime noise aliasing
        float crRot = cos(rot), srRot = sin(rot);
        vec2 q = vec2(p.x * crRot + p.y * srRot, -p.x * srRot + p.y * crRot);
        vec2 drift1 = vec2(-mod(sec * 0.48, 128.0), mod(sec * 0.34, 128.0));
        vec2 drift2 = vec2(mod(sec * 0.38, 128.0), -mod(sec * 0.56, 128.0));
        float curveWave = valNoise2(q * 3.0 + drift1);
        float curveWave2 = valNoise2(q * 4.2 + drift2 + vec2(seedVal * 7.0));
        float fNoise1 = fbm2(q * 3.4 + drift1);
        float fNoise2 = fbm2(q * 5.2 + drift2);
        float flameTex = clamp(fNoise1 * 0.60 + fNoise2 * 0.40, 0.0, 1.0);

        float diskMask = 1.0 - smoothstep(0.88, 0.99, r);
        float diskGlow = exp(-r * r * 3.4) * (1.0 - smoothstep(0.76, 1.0, r)) * 0.26;
        float centerPin = exp(-r * r * 36.0) * 1.05;

        // Blazing outer & inner rings
        float outerWarp = (curveWave - 0.5) * 0.015;
        float gOutCore = exp(-pow((r - (rOuter + outerWarp)) / (wFire * 1.12), 2.0)) * 1.44;
        float gOutHalo = exp(-pow((r - rOuter) / 0.056, 2.0)) * (0.60 + 0.76 * flameTex) * diskMask;
        float gInCore  = exp(-pow((r - (rInner + (curveWave2 - 0.5) * 0.012)) / (wFire * 1.12), 2.0)) * 1.28;
        float gMidRing = exp(-pow((r - 0.56) / wHair, 2.0)) * 0.56;

        // Outer tangential licking flame tips
        float nWhips = 6.0;
        float dWhip = abs(fract(((aRot + 2.2 * log(max(r / rOuter, 0.65)) + (curveWave - 0.5) * 0.08) * nWhips) / TAU + 0.5) - 0.5) * (TAU / nWhips) * r;
        float whipSpan = smoothstep(0.78, 0.85, r) * (1.0 - smoothstep(0.89, 0.99, r));
        float gOutWhips = exp(-pow(dWhip / max(0.018, pxW * 1.8), 2.0)) * whipSpan * (0.76 + 0.52 * flameTex);

        // Smooth 5-arm converging pinwheel flame ribbons
        float nArms = 5.0;
        float smoothWarp = (curveWave - 0.5) * 0.075;
        float armPhase = (aRot + 2.10 * log(max(r / rInner, 0.16)) + smoothWarp) * nArms;
        float dArm = abs(fract(armPhase / TAU + 0.5) - 0.5) * (TAU / nArms) * r;
        float armW = max(mix(0.010, 0.026, smoothstep(0.08, 0.55, r)) * (1.0 - 0.32 * smoothstep(0.68, 0.86, r)), pxW * 2.2);
        float swirlArms = (exp(-pow(dArm / (armW * 0.42), 2.0)) * 1.08 + exp(-pow(dArm / (armW * 1.70), 2.0)) * (0.48 + 0.64 * flameTex)) * diskMask;

        // Seamless hexagram reaching rOuter + 3 full intersecting eccentric sacred rings
        float sec6 = TAU / 6.0;
        float k6 = mod(aRot + sec6 * 0.5, sec6) - sec6 * 0.5;
        float k6b = mod(aRot + sec6, sec6) - sec6 * 0.5;
        float geomWin = smoothstep(0.18, 0.25, r) * (1.0 - smoothstep(0.82, 0.86, r));
        float gChord1 = exp(-pow(abs(r * cos(k6) - 0.71) / wHair, 2.0)) * 0.70;
        float gChord2 = exp(-pow(abs(r * cos(k6b) - 0.54) / wHair, 2.0)) * 0.54;
        float gOrbArc = 0.0;
        for (int ci = 0; ci < 3; ci++) {
            float ca = rot + float(ci) * (TAU / 3.0);
            vec2 cOff = vec2(cos(ca), sin(ca)) * 0.24;
            gOrbArc += exp(-pow(abs(length(p - cOff) - 0.585) / wHair, 2.0)) * 0.50;
        }
        float crispGeom = (gChord1 + gChord2 + min(gOrbArc, 0.90) + gMidRing) * geomWin;

        float fireIntensity = (diskGlow + centerPin + gOutCore + gOutHalo + gInCore + swirlArms + crispGeom) * diskMask + gOutWhips;
        float hotW = clamp(gOutCore * 0.70 + gInCore * 0.68 + crispGeom * 0.50 + centerPin * 0.74 + exp(-pow(dArm / max(0.008, pxW * 1.1), 2.0)) * 0.44, 0.0, 1.0);
        float whiteW = clamp(gOutCore * 0.38 + gInCore * 0.34 + centerPin * 0.58, 0.0, 1.0);

        vec3 crimsonVelvet = vec3(0.90, 0.10, 0.02);
        vec3 fieryOrange   = vec3(1.10, 0.40, 0.03);
        vec3 lemonGold     = vec3(1.20, 0.90, 0.22);
        vec3 whiteFlame    = vec3(1.30, 1.18, 0.78);

        color = mix(crimsonVelvet, fieryOrange, clamp(gOutHalo * 0.74 + swirlArms * 0.78 + gOutWhips * 0.82, 0.0, 1.0));
        color = mix(color, lemonGold, hotW);
        color = mix(color, whiteFlame, whiteW);
        alpha = clamp(fireIntensity * fade * ColorModulator.a, 0.0, 1.0);
    }

    if (alpha < 0.004) discard;
    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
