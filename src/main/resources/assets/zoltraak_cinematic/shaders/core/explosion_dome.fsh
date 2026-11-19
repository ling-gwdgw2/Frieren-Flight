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

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

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

// Fast 2x2x2 3D Worley boiling plasma cells (replaces beachball sine stripes with organic cellular boiling)
float worley3(vec3 p) {
    vec3 n = floor(p - 0.5);
    vec3 f = p - n;
    float md = 2.0;
    for (int k = 0; k <= 1; k++) {
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                vec3 g = vec3(float(i), float(j), float(k));
                vec3 o = hash33(n + g);
                vec3 r = g + 0.16 + 0.68 * o - f;
                md = min(md, dot(r, r));
            }
        }
    }
    return clamp(1.0 - sqrt(md) * 0.90, 0.0, 1.0);
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
    // <= 0.35 : cel-shaded boiling cellular ignition core (pairs with 3D raymarched mushroom volume)
    // 0.35..0.78: fast translucent supersonic spherical pressure shockwave shell
    // > 0.78   : wide soft volumetric exterior blast corona halo
    float shellLayer = vColor.b;
    float fade = clamp(vColor.a, 0.0, 1.0);

    float elev = clamp(vUv.y, 0.0, 1.0);
    float t = GameTime * 2.4 + progress * 2.8;

    float rawDot = dot(E, V);
    float frontOnly = smoothstep(-0.04, 0.12, rawDot);
    float ndotv = clamp(abs(rawDot), 0.0, 1.0);

    // 2-layer 3D domain warp for billowing organic plasma relief (computed before per-pixel discard for clean dFdx/dFdy)
    vec3 q = L * 3.4 + vec3(0.0, -t * 0.45, 0.0);
    vec3 warp = vec3(fbm3(q), fbm3(q + vec3(7.1, 3.3, 11.7)), fbm3(q + vec3(19.3, 5.1, 2.9))) - 0.5;
    float nWobble = fbm3(q + warp * 0.85);
    float boilCell = worley3(L * 4.4 + warp * 1.05 + vec3(0.0, -t * 0.52, 0.0));
    float boilFine = worley3(L * 8.2 + warp * 0.65 + vec3(0.0, -t * 0.88, 0.0));
    float cellular = clamp(boilCell * 0.68 + boilFine * 0.32, 0.0, 1.0);
    float bumpDx = dFdx(cellular);
    float bumpDy = dFdy(cellular);

    if (frontOnly < 0.02) discard;

    // Organic boiling ground-root feathering: eliminates any flat knife-cut equator at terrain contact
    float rootTurbulence = (nWobble * 0.58 + boilFine * 0.42 - 0.5) * 0.26;
    float rootMerge = smoothstep(-0.20, 0.26, L.y + rootTurbulence)
                    * smoothstep(0.0, 0.16, elev + (nWobble - 0.5) * 0.10);

    // Organic turbulent outer limb feathering: dissolves hard sphere rim into the exterior blast corona
    float innerLimbFade = smoothstep(0.02, 0.22 + (1.0 - cellular) * 0.18, ndotv);

    // 1. Wide Exterior Volumetric Bloom Corona Shell (1.48x radius)
    if (shellLayer > 0.78) {
        float haloLife = 1.0 - smoothstep(0.36, 0.76, progress);
        if (haloLife < 0.005) discard;

        float outsideLimb = clamp(ndotv / 0.73, 0.0, 1.0);
        float outsideGlow = pow(outsideLimb, 2.0) * smoothstep(0.0, 0.32, ndotv);
        float insideAtten = 1.0 - smoothstep(0.72, 0.95, ndotv) * 0.76;
        float glowStrength = (ndotv < 0.73 ? outsideGlow : (0.94 * insideAtten))
                           * rootMerge * (0.80 + 0.32 * cellular);

        vec3 crimsonOuter = vec3(1.06, 0.18, 0.02);
        vec3 amberBloom = vec3(1.22, 0.58, 0.08);
        vec3 goldBloom = vec3(1.30, 0.92, 0.26);
        vec3 col = mix(crimsonOuter, amberBloom, smoothstep(0.08, 0.70, ndotv));
        col = mix(col, goldBloom, clamp(heatFlash * 0.58, 0.0, 1.0));

        float alpha = clamp(glowStrength * haloLife * frontOnly * fade * 0.86 * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // 2. Fast Translucent Spherical Pressure Shockwave Dome (supersonic Mach wave shell)
    if (shellLayer > 0.35) {
        float rimWave = exp(-pow((ndotv - 0.15) * 8.5, 2.0)) * 0.86;
        float doubleRipple = exp(-pow((ndotv - 0.30) * 11.0, 2.0)) * 0.32;
        float shellFill = pow(1.0 - ndotv, 2.8) * 0.30;
        float shockFade = 1.0 - smoothstep(0.30, 0.96, progress);
        float alpha = clamp((rimWave + doubleRipple + shellFill) * shockFade * rootMerge * innerLimbFade * frontOnly * fade * 0.72 * ColorModulator.a, 0.0, 1.0);
        if (alpha < 0.004) discard;
        vec3 col = mix(vec3(1.06, 0.68, 0.26), vec3(1.34, 1.28, 1.14), clamp(rimWave, 0.0, 1.0));
        fragColor = vec4(col * ColorModulator.rgb, alpha);
        return;
    }

    // 3. Boiling Cel-Shaded Cellular Fireball Core (no beachball vertical stripes!)
    float fresnelRim = pow(1.0 - ndotv, 2.6);
    float poleCap = smoothstep(0.70, 0.94, elev);

    // Screen-space relief gradient on cellular heightfield for crisp anime top-lit shading
    float animeLight = clamp(0.52 + L.y * 0.34 - (bumpDx + bumpDy) * 9.0, 0.0, 1.0);

    // 4-tier anime cel-stepped plasma ramp: Crimson Rim -> Tangerine -> Lemon Gold -> Incandescent White-Hot
    float celBand1 = smoothstep(0.32, 0.44, cellular * 0.72 + animeLight * 0.38);
    float celBand2 = smoothstep(0.62, 0.74, cellular * 0.65 + animeLight * 0.35 + heatFlash * 0.42 + poleCap * 0.22);
    float creviceHot = pow(clamp(1.0 - boilCell, 0.0, 1.0), 2.4) * (1.0 - smoothstep(0.28, 0.62, progress));

    vec3 crimsonRim = vec3(1.06, 0.16, 0.02);
    vec3 tangerineOrange = vec3(1.08, 0.44, 0.03);
    vec3 lemonYellow = vec3(1.18, 0.98, 0.24);
    vec3 whiteHot = vec3(1.32, 1.26, 0.96);

    vec3 fireCol = mix(crimsonRim, tangerineOrange, celBand1);
    fireCol = mix(fireCol, lemonYellow, celBand2);
    fireCol = mix(fireCol, whiteHot, clamp(heatFlash * 0.78 + creviceHot * 0.52 + poleCap * 0.28 * (1.0 - progress), 0.0, 1.0));
    fireCol = mix(fireCol, crimsonRim, clamp(fresnelRim * 0.82, 0.0, 1.0));

    // Blend bottom crater skirt softly toward deep crimson-amber warmth
    float craterSkirtMix = 1.0 - smoothstep(-0.04, 0.28, L.y + (nWobble - 0.5) * 0.12);
    fireCol = mix(fireCol, vec3(1.02, 0.22, 0.03), craterSkirtMix * 0.68);

    // Cooling turbulent ash takeover + incandescent crevices handing off cleanly into the 3D raymarched mushroom cloud
    float sootTakeover = smoothstep(0.18, 0.46, progress);
    float plumeBreakup = clamp(cellular * 0.62 + nWobble * 0.38, 0.0, 1.0);
    vec3 darkAsh = vec3(0.16, 0.10, 0.08);
    vec3 magmaGlow = vec3(1.14, 0.52, 0.08);
    vec3 sootCol = mix(darkAsh, magmaGlow, clamp(creviceHot * 1.25 + (1.0 - smoothstep(0.25, 0.58, progress)) * plumeBreakup * 0.55, 0.0, 1.0));

    vec3 surfaceCol = mix(fireCol, sootCol, sootTakeover * smoothstep(0.18, 0.56, plumeBreakup));

    float dissolveProgress = smoothstep(0.22, 0.62, progress);
    float plumeSurvival = elev * 0.22 + plumeBreakup * 0.64 + boilFine * 0.18;
    float cutoff = dissolveProgress * 1.18;
    float plumeAlpha = smoothstep(cutoff - 0.20, cutoff + 0.10, plumeSurvival);

    float lateFade = 1.0 - smoothstep(0.28, 0.64, progress);
    float domeAlpha = clamp(rootMerge * innerLimbFade * plumeAlpha * lateFade * frontOnly * fade * ColorModulator.a, 0.0, 1.0);
    if (domeAlpha < 0.010) discard;

    fragColor = vec4(surfaceCol * ColorModulator.rgb, domeAlpha);
}
