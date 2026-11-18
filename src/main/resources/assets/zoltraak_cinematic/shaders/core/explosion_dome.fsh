#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec3 vEyeNormal;
in vec3 vViewDir;
in vec3 vLocalDir;
in vec4 vColor;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

float fbm3(vec3 p) {
    float v = 0.54 * valNoise3(p);
    p = p * 2.05 + vec3(13.1, 7.7, 19.3);
    v += 0.30 * valNoise3(p);
    p = p * 2.07 + vec3(5.3, 17.9, 11.1);
    v += 0.16 * valNoise3(p);
    return v;
}

void main() {
    float eyeLen = length(vEyeNormal);
    vec3 E = eyeLen > 0.0001 ? vEyeNormal / eyeLen : vec3(0.0, 0.0, 1.0);
    float viewLen = length(vViewDir);
    vec3 V = viewLen > 0.0001 ? vViewDir / viewLen : vec3(0.0, 0.0, 1.0);
    float dirLen = length(vLocalDir);
    vec3 L = dirLen > 0.0001 ? vLocalDir / dirLen : vec3(0.0, 1.0, 0.0);

    float progress = clamp(vColor.r, 0.0, 1.0);
    float heatFlash = clamp(vColor.g, 0.0, 1.0);
    // shellLayer encoding:
    // <= 0.35 : stylized cel sunburst dome -> dark magma blotches -> painterly starburst soot dissolve + lingering ember glow
    // 0.35..0.78: fast translucent spherical pressure shockwave shell
    // > 0.78   : wide soft volumetric exterior bloom halo around fireball
    float shellLayer = vColor.b;
    float fade = clamp(vColor.a, 0.0, 1.0);

    float elev = clamp(vUv.y, 0.0, 1.0);
    float theta = vUv.x * 6.2831853; // periodic 2*PI wrap -> zero azimuthal seam
    float t = GameTime * 2.4 + progress * 2.8;

    float rawDot = dot(E, V);
    float frontOnly = smoothstep(-0.04, 0.12, rawDot);
    float ndotv = clamp(abs(rawDot), 0.0, 1.0);
    if (frontOnly < 0.02) discard;

    float nWobble = fbm3(L * 3.2 + vec3(0.0, -t * 0.48, 0.0));
    float nDetail = fbm3(L * 6.4 + vec3(0.0, -t * 0.78, 0.0));
    float warpTheta = (nWobble - 0.5) * 0.22;

    // Organic boiling ground-root feathering: eliminates any flat knife-cut equator at terrain contact
    float rootTurbulence = (nWobble * 0.58 + nDetail * 0.42 - 0.5) * 0.26;
    float rootMerge = smoothstep(-0.20, 0.26, L.y + rootTurbulence)
                    * smoothstep(0.0, 0.16, elev + (nWobble - 0.5) * 0.10);

    // Organic turbulent outer limb feathering: dissolves hard sphere rim into the exterior blast corona
    float limbNoise = clamp(nWobble * 0.65 + nDetail * 0.35, 0.0, 1.0);
    float innerLimbFade = smoothstep(0.02, 0.24 + (1.0 - limbNoise) * 0.18, ndotv);

    // 1. Wide Exterior Volumetric Bloom Corona Shell (1.48x radius)
    if (shellLayer > 0.78) {
        float haloLife = 1.0 - smoothstep(0.32, 0.72, progress);
        if (haloLife < 0.005) discard;

        // With 1.48x outer shell around 1.0x inner dome: limb of 1.0x dome projects to ndotv ~ 0.73.
        // Glow swells seamlessly across the boiling boundary and feathers outward to 0 at silhouette.
        float outsideLimb = clamp(ndotv / 0.73, 0.0, 1.0);
        float outsideGlow = pow(outsideLimb, 2.0) * smoothstep(0.0, 0.32, ndotv);
        float insideAtten = 1.0 - smoothstep(0.72, 0.95, ndotv) * 0.76;
        float glowStrength = (ndotv < 0.73 ? outsideGlow : (0.94 * insideAtten))
                           * rootMerge * (0.82 + 0.28 * nWobble);

        vec3 crimsonOuter = vec3(1.06, 0.18, 0.02);
        vec3 amberBloom = vec3(1.20, 0.56, 0.08);
        vec3 goldBloom = vec3(1.28, 0.90, 0.24);
        vec3 col = mix(crimsonOuter, amberBloom, smoothstep(0.08, 0.70, ndotv));
        col = mix(col, goldBloom, clamp(heatFlash * 0.55, 0.0, 1.0));

        float alpha = clamp(glowStrength * haloLife * frontOnly * fade * 0.86 * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // 2. Fast Translucent Spherical Pressure Shockwave Dome (early blast expansion)
    if (shellLayer > 0.35) {
        float rimWave = exp(-pow((ndotv - 0.16) * 8.0, 2.0)) * 0.78;
        float shellFill = pow(1.0 - ndotv, 2.8) * 0.34;
        float shockFade = 1.0 - smoothstep(0.32, 0.96, progress);
        float alpha = clamp((rimWave + shellFill) * shockFade * rootMerge * innerLimbFade * frontOnly * fade * 0.68 * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        vec3 col = mix(vec3(1.05, 0.68, 0.28), vec3(1.30, 1.25, 1.10), clamp(rimWave, 0.0, 1.0));
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // 3. Main Pyroclastic Fireball Dome -> Boiling Amber-Soot Plumes -> Early Wispy Dissolve into Dust Skirt
    float tierShift = smoothstep(0.40, 0.62, elev) * 0.24;
    float broad1 = sin(theta * 14.0 + tierShift + warpTheta * 2.8) * 0.5 + 0.5;
    float broad2 = sin(theta * 22.0 - tierShift * 1.4 - warpTheta * 3.2) * 0.5 + 0.5;
    float celYellowBand = smoothstep(0.46, 0.58, broad1 * 0.68 + broad2 * 0.32);

    // 3D boiling cauliflower turbulence breaks up artificial stripe alignment during cooling & dissolve
    vec3 plumeCoord = L * vec3(4.2, 2.8, 4.2) + vec3(0.0, -progress * 2.2 - t * 0.18, 0.0);
    float crustNoise = fbm3(plumeCoord + vec3(nWobble * 0.65));
    float plumeBreakup = clamp(crustNoise * 0.70 + nDetail * 0.38, 0.0, 1.0);

    // Stage 1 (0.00..0.34): Cel-Shaded Lemon-Yellow & Tangerine-Orange Sunburst + Soft Crimson Fresnel Rim
    float fresnelRim = pow(1.0 - ndotv, 2.8);
    float poleCap = smoothstep(0.74, 0.94, elev);
    float midBand = exp(-pow((elev - 0.50) * 6.0, 2.0)) * 0.30;

    vec3 crimsonRim = vec3(1.05, 0.18, 0.02);
    vec3 tangerineOrange = vec3(1.04, 0.44, 0.02);
    vec3 lemonYellow = vec3(1.12, 0.98, 0.20);
    vec3 whiteHot = vec3(1.28, 1.22, 0.90);

    float yellowMask = clamp(celYellowBand + poleCap * 0.65 + midBand * celYellowBand, 0.0, 1.0);
    vec3 fireCol = mix(tangerineOrange, lemonYellow, yellowMask);
    fireCol = mix(fireCol, whiteHot, clamp(heatFlash * 0.82 + poleCap * 0.35 * (1.0 - progress), 0.0, 1.0));
    fireCol = mix(fireCol, crimsonRim, clamp(fresnelRim * 0.90, 0.0, 1.0));

    // Blend bottom crater skirt softly toward deep crimson-amber warmth so base melts into dust & ground glow
    float craterSkirtMix = 1.0 - smoothstep(-0.04, 0.28, L.y + (nWobble - 0.5) * 0.12);
    fireCol = mix(fireCol, vec3(1.02, 0.24, 0.03), craterSkirtMix * 0.72);

    // Cooling turbulent magma crust around lower/mid dome (0.08..0.44)
    float blotchZone = (1.0 - smoothstep(0.14, 0.76, elev)) * smoothstep(0.0, 0.08, progress);
    float blotchField = crustNoise + (1.0 - elev) * 0.24 + smoothstep(0.14, 0.42, progress) * 0.54;
    float isBlotch = smoothstep(0.64, 0.72, blotchField * (0.48 + 0.52 * blotchZone + smoothstep(0.22, 0.42, progress)));
    float blotchRim = smoothstep(0.58, 0.64, blotchField) * (1.0 - isBlotch);

    // Stage 2 (0.24..0.52): Glowing amber-crimson core wrapped in turbulent billowing ember-smoke plumes
    float sootTakeover = smoothstep(0.24, 0.48, progress);
    float fullSootMask = clamp(isBlotch + sootTakeover * smoothstep(0.14, 0.46, plumeBreakup + (1.0 - elev) * 0.16), 0.0, 1.0);

    vec3 darkCharcoal = vec3(0.20, 0.13, 0.09);
    vec3 smokyAmber = vec3(0.84, 0.36, 0.07);
    float innerAmberGlow = (1.0 - smoothstep(0.30, 0.64, progress)) * (0.35 + 0.65 * plumeBreakup);
    vec3 sootCol = mix(darkCharcoal, smokyAmber, innerAmberGlow * (1.0 - isBlotch * 0.55));
    sootCol = mix(sootCol, vec3(1.12, 0.48, 0.06), clamp(fresnelRim * (1.0 - smoothstep(0.38, 0.64, progress)) * 0.88, 0.0, 1.0));

    vec3 surfaceCol = mix(fireCol, sootCol, fullSootMask);
    surfaceCol = mix(surfaceCol, lemonYellow, blotchRim * 0.68 * (1.0 - sootTakeover));

    // Stage 3 (0.28..0.70): Turbulent volumetric dissolve -> smoothly hands off into billowing smoke collars
    float dissolveProgress = smoothstep(0.28, 0.68, progress);
    float plumeSurvival = elev * 0.24 + plumeBreakup * 0.62 + nDetail * 0.18;
    float cutoff = dissolveProgress * 1.16;
    float plumeAlpha = smoothstep(cutoff - 0.22, cutoff + 0.10, plumeSurvival);

    float lateFade = 1.0 - smoothstep(0.34, 0.70, progress);
    float domeAlpha = clamp(rootMerge * innerLimbFade * plumeAlpha * lateFade * frontOnly * fade * ColorModulator.a, 0.0, 1.0);
    if (domeAlpha < 0.010) discard;

    fragColor = vec4(surfaceCol * ColorModulator.rgb, domeAlpha);
}
