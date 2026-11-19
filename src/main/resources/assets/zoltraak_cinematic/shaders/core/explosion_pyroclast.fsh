#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// 3D Volumetric Raymarched Anime Mushroom Cloud & Pyroclastic Fireball

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec4 vColor;
in vec3 vLocalPos;
in vec3 vViewPos;
in vec3 vCamToFragWorld;

out vec4 fragColor;

float ign(vec2 fragCoord, float frameOffset) {
    vec2 p = fragCoord + vec2(frameOffset * 5.588238);
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float snoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = hash33(i + vec3(0.0, 0.0, 0.0)).x;
    float n100 = hash33(i + vec3(1.0, 0.0, 0.0)).x;
    float n010 = hash33(i + vec3(0.0, 1.0, 0.0)).x;
    float n110 = hash33(i + vec3(1.0, 1.0, 0.0)).x;
    float n001 = hash33(i + vec3(0.0, 0.0, 1.0)).x;
    float n101 = hash33(i + vec3(1.0, 0.0, 1.0)).x;
    float n011 = hash33(i + vec3(0.0, 1.0, 1.0)).x;
    float n111 = hash33(i + vec3(1.0, 1.0, 1.0)).x;

    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    return mix(mix(nx00, nx10, u.y), mix(nx01, nx11, u.y), u.z);
}

// Fast 2x2x2 Worley spherical smoke billows (8 cell taps)
float worleyBillow3(vec3 p) {
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

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / max(k, 1.0e-5);
    return min(a, b) - h * h * k * 0.25;
}

float sdTorus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

float evalPyroBillow(vec3 p) {
    float w1 = worleyBillow3(p);
    float w2 = worleyBillow3(p * 2.08 + vec3(7.3, 13.1, 4.7));
    float d3 = snoise3(p * 4.4 + vec3(2.1, 5.7, 9.3));
    return clamp(w1 * 0.58 + w2 * 0.30 + d3 * 0.12, 0.0, 1.0);
}

// Multi-part Anime Mushroom Cloud SDF: Rising Toroidal Cap + Fire Stem + Ground Base Surge
float evalMushroomField(vec3 p, float progress, float seedShift, out float heat, out float billow) {
    float rBound = length(p);
    if (rBound > 0.96 || p.y < -0.58) {
        heat = 0.0;
        billow = 0.0;
        return 0.0;
    }

    float grow = pow(clamp(progress, 0.0, 1.0), 0.38);
    float rise = smoothstep(0.08, 0.92, progress);

    // 1. Rising Mushroom Cap (starts low wide fireball, buoys upward into flattened toroidal anvil cap)
    float capY = mix(-0.18, 0.38, rise);
    float capRadXZ = mix(0.34, 0.62, grow);
    float capRadY = mix(0.32, 0.23, smoothstep(0.14, 0.72, progress));
    vec3 capP = p - vec3(0.0, capY, 0.0);
    float dCapDome = (length(vec3(capP.x / capRadXZ, capP.y / capRadY, capP.z / capRadXZ)) - 1.0) * min(capRadXZ, capRadY);
    float torusR = capRadXZ * 0.62;
    float torusTube = mix(0.12, 0.20, smoothstep(0.10, 0.65, progress));
    float dCapRing = sdTorus(capP + vec3(0.0, 0.05 * rise, 0.0), vec2(torusR, torusTube));
    float dCap = smin(dCapDome, dCapRing, 0.14);

    // 2. Central Rising Pyroclastic Fire Stem connecting crater floor to mushroom underside
    float stemTop = capY + 0.06;
    float stemBottom = -0.44;
    float stemY = clamp(p.y, stemBottom, stemTop);
    float stemNormH = clamp((p.y - stemBottom) / max(stemTop - stemBottom, 0.05), 0.0, 1.0);
    float stemFlare = 1.0 + 0.45 * pow(1.0 - stemNormH, 2.0) + 0.22 * pow(stemNormH, 3.0);
    float stemRadius = mix(0.24, 0.14, smoothstep(0.05, 0.75, progress)) * stemFlare;
    float dStem = length(vec3(p.x, p.y - stemY, p.z)) - stemRadius;

    // 3. Rolling Pyroclastic Ground Base Surge Torus hugging terrain seamlessly
    float surgeR = mix(0.22, 0.68, pow(clamp(progress, 0.0, 1.0), 0.46));
    float surgeTube = mix(0.11, 0.14, smoothstep(0.0, 0.40, progress)) * (1.0 - 0.35 * smoothstep(0.65, 1.0, progress));
    float dSurge = sdTorus(p - vec3(0.0, -0.35, 0.0), vec2(surgeR, surgeTube));

    float sdfBase = smin(dCap, dStem, 0.16);
    sdfBase = smin(sdfBase, dSurge, 0.14);

    // Buoyant upward + toroidal convection advection & 3D domain warping
    float advSpeed = progress * 2.15 + GameTime * 0.18;
    vec3 q = p * (3.70 - 0.65 * progress) - vec3(0.0, advSpeed, 0.0) + vec3(seedShift);
    vec3 warpVec = vec3(
        snoise3(q * 0.72),
        snoise3(q * 0.72 + vec3(11.3, 4.1, 7.7)),
        snoise3(q * 0.72 + vec3(23.1, 13.7, 2.9))
    ) - 0.5;
    float f = evalPyroBillow(q + warpVec * 0.70);
    float lobe = smoothstep(0.10, 0.84, f);

    // Cauliflower knuckles push outward while pinched crevices carve inward
    float displacedSdf = sdfBase - (lobe - 0.38) * mix(0.22, 0.30, grow);

    // Smooth sphere boundary + sub-ground feathering (zero hard clip on terrain or sphere edge)
    float boundSoft = 1.0 - smoothstep(0.76, 0.96, rBound);
    float groundSoft = smoothstep(-0.54, -0.36, p.y + (f - 0.5) * 0.10);
    float baseDensity = smoothstep(0.12, -0.10, displacedSdf) * boundSoft * groundSoft;

    // Late-stage wispy turbulent smoke dissolution
    float erodeCut = smoothstep(0.50, 0.98, progress) * 0.68;
    float erodeMask = smoothstep(erodeCut * 0.60, erodeCut + 0.28, f);
    float density = baseDensity * mix(1.0, erodeMask, smoothstep(0.46, 0.82, progress));

    // Incandescent core plasma & pinched Worley magma fissures glowing violently from inside ash folds
    float crevice = pow(clamp(1.0 - lobe, 0.0, 1.0), 2.1);
    float distCore = length(p - vec3(0.0, mix(-0.16, 0.20, rise * 0.65), 0.0));
    float coreScale = max(0.16, mix(0.46, 0.22, progress));
    float coreHeat = exp(-pow(distCore / coreScale, 1.85)) * (1.65 - 1.05 * progress);
    float stemHeat = exp(-pow(length(p.xz) / max(stemRadius * 1.15, 0.06), 2.0)) * (1.0 - smoothstep(-0.44, capY + 0.22, p.y) * 0.35) * (1.25 - 0.88 * progress);
    float fissureHeat = crevice * smoothstep(0.06, -0.16, displacedSdf) * (1.95 - 1.35 * progress);
    float ignitionBurst = exp(-progress * 6.5) * 2.15 * smoothstep(0.14, -0.06, displacedSdf);

    heat = clamp(coreHeat + stemHeat * 0.58 + fissureHeat + ignitionBurst, 0.0, 2.8);
    billow = lobe;
    return density;
}

void main() {
    float rayLen = length(vCamToFragWorld);
    if (rayLen < 0.0001) discard;
    vec3 rd = vCamToFragWorld / rayLen;

    float boundR = max(vUv.x, 0.1);
    float seedNorm = vUv.y;
    float progress = clamp(vColor.r, 0.0, 1.0);
    float heatFlash = clamp(vColor.g, 0.0, 1.0);
    bool isInside = vColor.b > 0.5;
    float fade = clamp(vColor.a, 0.0, 1.0);

    vec3 pStart;
    float maxTravel;

    if (isInside) {
        if (gl_FrontFacing) discard;
        vec3 pExit = normalize(vLocalPos);
        float distNorm = clamp(length(vViewPos) / boundR, 0.01, 2.5);
        vec3 pCam = pExit - rd * distNorm;
        float cc = dot(pCam, pCam) - 1.0;
        if (cc > 0.0) {
            float bb = dot(pCam, rd);
            float disc = bb * bb - cc;
            if (disc <= 0.0) discard;
            float tEnt = max(0.0, -bb - sqrt(disc));
            pStart = pCam + rd * tEnt;
            maxTravel = max(0.0, distNorm - tEnt);
        } else {
            pStart = pCam;
            maxTravel = distNorm;
        }
    } else {
        if (!gl_FrontFacing) discard;
        vec3 pEnter = normalize(vLocalPos);
        float b = dot(pEnter, rd);
        if (b >= -0.001) discard;
        pStart = pEnter;
        maxTravel = -2.0 * b;
    }

    const int STEPS = 28;
    float stepSize = maxTravel / float(STEPS);
    float jitter = ign(gl_FragCoord.xy, fract(GameTime * 19.0) * 32.0);
    float travel = stepSize * jitter;
    float seedShift = 3.7 + seedNorm * 29.0;

    vec3 lightDir = normalize(vec3(0.40, 0.88, 0.25));
    // Henyey-Greenstein forward scattering bias for glowing anime smoke rims
    float cosTheta = dot(rd, lightDir);
    float hgPhase = 0.72 + 0.42 * (0.55 / pow(max(1.30 - 1.10 * cosTheta, 0.15), 1.5));

    vec4 sum = vec4(0.0);

    for (int i = 0; i < STEPS; i++) {
        if (sum.a > 0.96 || travel >= maxTravel) break;
        vec3 p = pStart + rd * travel;

        float heat, billow;
        float den = evalMushroomField(p, progress, seedShift, heat, billow);

        if (den > 0.014) {
            // Directional light self-shadowing across neighboring Worley mushroom billows
            float dummyH, dummyB;
            float denLit = evalMushroomField(p + lightDir * 0.10, progress, seedShift, dummyH, dummyB);
            float rawShadow = clamp(0.24 + (den - denLit) * 1.65 + clamp(p.y * 0.42 + 0.16, -0.10, 0.42), 0.08, 1.25);

            // Stylized anime cel-stepped luminance shelving mixed with smooth volumetric lighting
            float celShelf = smoothstep(0.26, 0.40, rawShadow) * 0.46 + smoothstep(0.62, 0.76, rawShadow) * 0.54;
            float litFactor = mix(rawShadow, celShelf * 1.12, 0.50);

            // Internal underside fire-bounce illuminating bottom of mushroom cap & base surge
            float underFire = clamp(exp(-length(p - vec3(0.0, -0.25, 0.0)) * 2.2) * (1.30 - 0.85 * progress), 0.0, 1.0);

            // Anime volcanic ash & blackbody magma palette (crimson/tangerine/lemon-gold/cream-white)
            vec3 darkVolcanicAsh = vec3(0.08, 0.055, 0.055);
            vec3 warmLitSmoke = vec3(0.76, 0.58, 0.44) * litFactor * hgPhase + vec3(1.06, 0.44, 0.08) * underFire * 0.82;
            vec3 deepCrimsonFlame = vec3(1.06, 0.18, 0.03) * (1.05 + 0.35 * heatFlash);
            vec3 moltenGold = vec3(1.22, 0.86, 0.18) * (1.10 + 0.45 * heatFlash);
            vec3 whiteIncandescent = vec3(1.42, 1.32, 1.02) * (1.15 + 0.55 * heatFlash);

            vec3 smokeCol = mix(darkVolcanicAsh, warmLitSmoke, clamp(litFactor * 0.78, 0.0, 1.0));
            vec3 sampleRgb = mix(smokeCol, deepCrimsonFlame, smoothstep(0.20, 0.58, heat));
            sampleRgb = mix(sampleRgb, moltenGold, smoothstep(0.56, 1.12, heat));
            sampleRgb = mix(sampleRgb, whiteIncandescent, smoothstep(1.10, 1.85, heat));

            float nearEyeFade = isInside ? smoothstep(0.05, 0.25, travel) : 1.0;
            float sampleAlpha = clamp(den * nearEyeFade * stepSize * mix(7.8, 4.6, smoothstep(0.35, 1.0, progress)), 0.0, 0.96);
            float oneMinusA = 1.0 - sum.a;
            sum.rgb += sampleRgb * sampleAlpha * oneMinusA;
            sum.a += sampleAlpha * oneMinusA;
        }

        travel += stepSize;
    }

    float finalAlpha = clamp(sum.a * fade * ColorModulator.a, 0.0, 1.0);
    if (finalAlpha < 0.004) discard;

    vec3 straightRgb = sum.rgb / max(sum.a, 0.001);
    fragColor = vec4(straightRgb * ColorModulator.rgb, finalAlpha);
}
