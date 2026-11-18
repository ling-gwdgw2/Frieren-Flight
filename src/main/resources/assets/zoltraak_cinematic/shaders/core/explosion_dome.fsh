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

    float nWobble = fbm3(L * 3.6 + vec3(0.0, -t * 0.55, 0.0));
    float warpTheta = (nWobble - 0.5) * 0.18;

    // 1. Wide Exterior Volumetric Bloom Corona Shell (1.38x radius)
    if (shellLayer > 0.78) {
        float haloLife = 1.0 - smoothstep(0.28, 0.56, progress);
        if (haloLife < 0.005) discard;

        // With 1.38x outer sphere around 1.0x inner dome: limb of 1.0x dome projects to ndotv ~ 0.69.
        // Glow peaks seamlessly right at the 1.0x dome silhouette (ndotv ~ 0.69) and decays smoothly
        // outward to 0 at silhouette (ndotv = 0) with ZERO dark air gap!
        float outsideLimb = clamp(ndotv / 0.69, 0.0, 1.0);
        float outsideGlow = pow(outsideLimb, 2.4) * smoothstep(0.0, 0.25, ndotv);
        float insideAtten = 1.0 - smoothstep(0.68, 0.92, ndotv) * 0.78;
        float glowStrength = (ndotv < 0.69 ? outsideGlow : (0.95 * insideAtten)) * smoothstep(0.0, 0.26, elev) * (0.88 + 0.20 * nWobble);

        vec3 crimsonOuter = vec3(1.08, 0.18, 0.02);
        vec3 amberBloom = vec3(1.20, 0.58, 0.08);
        vec3 goldBloom = vec3(1.28, 0.90, 0.24);
        vec3 col = mix(crimsonOuter, amberBloom, smoothstep(0.10, 0.68, ndotv));
        col = mix(col, goldBloom, clamp(heatFlash * 0.55, 0.0, 1.0));

        float alpha = clamp(glowStrength * haloLife * frontOnly * fade * 0.85 * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // 2. Fast Translucent Spherical Pressure Shockwave Dome (early blast expansion)
    if (shellLayer > 0.35) {
        float rimWave = exp(-pow((ndotv - 0.14) * 9.0, 2.0)) * 0.90;
        float shellFill = pow(1.0 - ndotv, 2.6) * 0.45;
        float shockFade = 1.0 - smoothstep(0.35, 1.0, progress);
        float alpha = clamp((rimWave + shellFill) * shockFade * frontOnly * fade * 0.72 * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        vec3 col = mix(vec3(1.05, 0.68, 0.28), vec3(1.30, 1.25, 1.10), clamp(rimWave, 0.0, 1.0));
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // 3. Main Fireball Dome -> Cooling Magma -> Painterly Starburst Soot Plumes -> Amber Afterglow
    float tierShift = smoothstep(0.44, 0.54, elev) * 0.26;
    float broad1 = sin(theta * 14.0 + tierShift + warpTheta * 2.5) * 0.5 + 0.5;
    float broad2 = sin(theta * 22.0 - tierShift * 1.4 - warpTheta * 3.0) * 0.5 + 0.5;
    float celYellowBand = smoothstep(0.46, 0.58, broad1 * 0.68 + broad2 * 0.32);

    // Painterly smoky starburst petals (rich dark charcoal radial plumes)
    float sp1 = sin(theta * 14.0 + warpTheta * 2.2) * 0.5 + 0.5;
    float sp2 = sin(theta * 24.0 - warpTheta * 2.6 + 1.0) * 0.5 + 0.5;
    float smokyPetals = clamp(pow(sp1, 1.30) * 0.54 + pow(sp2, 1.60) * 0.34 + nWobble * 0.16, 0.0, 1.0);

    float crustNoise = fbm3(L * 5.2 + vec3(0.0, -progress * 2.0, 0.0));

    // Stage 1 (0.00..0.38): Cel-Shaded Lemon-Yellow & Tangerine-Orange Sunburst + Intense Red Fresnel Rim
    float fresnelRim = pow(1.0 - ndotv, 3.2);
    float baseRim = smoothstep(0.0, 0.04, elev);
    float poleCap = smoothstep(0.76, 0.95, elev);
    float midBand = exp(-pow((elev - 0.50) * 6.5, 2.0)) * 0.32;

    vec3 crimsonRim = vec3(1.05, 0.16, 0.01);
    vec3 tangerineOrange = vec3(1.04, 0.44, 0.02);
    vec3 lemonYellow = vec3(1.12, 0.98, 0.20);
    vec3 whiteHot = vec3(1.28, 1.22, 0.90);

    float yellowMask = clamp(celYellowBand + poleCap * 0.65 + midBand * celYellowBand, 0.0, 1.0);
    vec3 fireCol = mix(tangerineOrange, lemonYellow, yellowMask);
    fireCol = mix(fireCol, whiteHot, clamp(heatFlash * 0.82 + poleCap * 0.35 * (1.0 - progress), 0.0, 1.0));
    fireCol = mix(fireCol, crimsonRim, clamp(fresnelRim * 0.94, 0.0, 1.0));

    // Cooling dark magma blotches in lower-mid latitudes (0.06..0.45)
    float blotchZone = (1.0 - smoothstep(0.16, 0.78, elev)) * smoothstep(0.0, 0.08, progress);
    float blotchField = crustNoise + (1.0 - elev) * 0.26 + smoothstep(0.16, 0.48, progress) * 0.56;
    float isBlotch = smoothstep(0.66, 0.73, blotchField * (0.48 + 0.52 * blotchZone + smoothstep(0.24, 0.46, progress)));
    float blotchRim = smoothstep(0.60, 0.66, blotchField) * (1.0 - isBlotch);

    // Stage 2 (0.28..0.58): Glowing amber-crimson core under darkening smoky charcoal clouds + orange rim
    float sootTakeover = smoothstep(0.26, 0.50, progress);
    float fullSootMask = clamp(isBlotch + sootTakeover * smoothstep(0.12, 0.45, crustNoise + (1.0 - elev) * 0.20), 0.0, 1.0);

    vec3 darkCharcoal = vec3(0.09, 0.06, 0.04);
    vec3 smokyAmber = vec3(0.68, 0.28, 0.05);
    float innerAmberGlow = (1.0 - smoothstep(0.36, 0.72, progress)) * (0.35 + 0.65 * crustNoise);
    vec3 sootCol = mix(darkCharcoal, smokyAmber, innerAmberGlow * (1.0 - isBlotch * 0.72));
    // Fiery orange glowing silhouette rim fading cleanly during starburst dissolve
    sootCol = mix(sootCol, vec3(1.10, 0.46, 0.05), clamp(fresnelRim * (1.0 - smoothstep(0.46, 0.72, progress)) * 0.88, 0.0, 1.0));

    vec3 surfaceCol = mix(fireCol, sootCol, fullSootMask);
    surfaceCol = mix(surfaceCol, lemonYellow, blotchRim * 0.68 * (1.0 - sootTakeover));

    // Stage 3 (0.36..0.90): Soft Feathered Painterly Starburst Smoke Dissolve
    float dissolveProgress = smoothstep(0.38, 0.88, progress);
    float petalSurvival = elev * 0.40 + smokyPetals * 0.50 + crustNoise * 0.14;
    float cutoff = dissolveProgress * 1.05;
    float petalAlpha = smoothstep(cutoff - 0.14, cutoff + 0.06, petalSurvival);

    float lateFade = 1.0 - smoothstep(0.64, 0.90, progress);
    float domeAlpha = clamp(baseRim * petalAlpha * lateFade * frontOnly * fade * ColorModulator.a, 0.0, 1.0);
    if (domeAlpha < 0.012) discard;

    fragColor = vec4(surfaceCol * ColorModulator.rgb, domeAlpha);
}
