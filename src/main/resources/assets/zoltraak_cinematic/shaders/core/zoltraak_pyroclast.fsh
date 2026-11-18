#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

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

// Smooth 3D gradient noise for subtle domain warping
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

// Compact 2x2x2 Worley cauliflower billow: returns rounded spherical puff height in [0, 1]
// Fast (only 8 cell checks!) with zero spherical smoothing collapse!
float worleyBillow3(vec3 p) {
    vec3 n = floor(p - 0.5);
    vec3 f = p - n;
    float md = 2.0;
    for (int k = 0; k <= 1; k++) {
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                vec3 g = vec3(float(i), float(j), float(k));
                vec3 o = hash33(n + g);
                vec3 r = g + 0.15 + 0.70 * o - f;
                float d = dot(r, r);
                md = min(md, d);
            }
        }
    }
    return clamp(1.0 - sqrt(md) * 0.92, 0.0, 1.0);
}

float pyroBillow(vec3 p) {
    // 2 octaves of 2x2x2 Worley spherical smoke puffs + 1 high-freq detail noise
    float w1 = worleyBillow3(p);
    float w2 = worleyBillow3(p * 2.05 + vec3(7.3, 13.1, 4.7));
    float d3 = snoise3(p * 4.3 + vec3(2.1, 5.7, 9.3));
    return clamp(w1 * 0.58 + w2 * 0.30 + d3 * 0.12, 0.0, 1.0);
}

float evalPyroField(vec3 p, float progress, float seedShift, out float heat, out float billow) {
    float r = length(p);
    if (r > 0.94) {
        heat = 0.0;
        billow = 0.0;
        return 0.0;
    }

    // Monotonic outward expansion removes radial fold lines in 3D advection!
    // Scale noise slightly smaller as explosion grows so billows roll smoothly outward.

    // Wide ground base surge + rising cauliflower crown contained inside unit sphere
    float baseSurge = exp(-pow((p.y + 0.28) * 3.8, 2.0)) * 0.16 * smoothstep(0.04, 0.65, progress);
    vec3 shapedP = p * vec3(1.0 - baseSurge, 1.02 - 0.10 * smoothstep(0.2, 0.9, progress), 1.0 - baseSurge);
    float shapedR = length(shapedP);

    float blastRadius = mix(0.32, 0.68, pow(clamp(progress, 0.0, 1.0), 0.35));

    // Monotonic outward scaling + buoyant upward advection (zero radial singularity fold!)
    vec3 q = p * (3.60 - 0.75 * progress) - vec3(0.0, progress * 1.65, 0.0) + vec3(seedShift);
    vec3 warpVec = vec3(snoise3(q * 0.7), snoise3(q * 0.7 + 11.1), snoise3(q * 0.7 + 23.7)) - 0.5;
    float f = pyroBillow(q + warpVec * 0.65);

    // Dramatic cauliflower knuckles protruding outward and deep pinched crevices between lobes
    float lobe = smoothstep(0.12, 0.82, f);
    float edgeRadius = blastRadius * (0.38 + 0.76 * lobe);
    float boundSoft = 1.0 - smoothstep(0.76, 0.94, r);

    float sdf = shapedR - edgeRadius;
    float baseDensity = smoothstep(0.13, -0.09, sdf) * boundSoft;

    // Clean late-stage dissolution breaking into ragged smoke wisps
    float erodeCut = smoothstep(0.45, 0.98, progress) * 0.68;
    float erodeMask = smoothstep(erodeCut * 0.62, erodeCut + 0.30, f);
    float density = baseDensity * mix(1.0, erodeMask, smoothstep(0.42, 0.78, progress));

    // Pinched Voronoi valleys expose superheated incandescent plasma inside cool smoke folds
    float crevice = pow(clamp(1.0 - lobe, 0.0, 1.0), 2.1);
    float coreScale = max(0.12, blastRadius * (0.64 - 0.45 * progress));
    float coreHeat = exp(-pow(shapedR / coreScale, 1.9)) * 1.40;
    float fissureHeat = crevice * smoothstep(edgeRadius, 0.02, shapedR) * (1.85 - 1.25 * progress);
    float flashBurst = exp(-progress * 7.5) * 2.10 * smoothstep(edgeRadius + 0.06, 0.02, shapedR);

    heat = clamp(coreHeat + fissureHeat + flashBurst, 0.0, 2.5);
    billow = lobe;
    return density;
}

void main() {
    float rayLen = length(vCamToFragWorld);
    if (rayLen < 0.0001) discard;
    vec3 rd = vCamToFragWorld / rayLen;

    float boundR = max(vUv.x, 0.1);
    bool isBlack = vUv.y > 0.5;
    float progress = clamp(vColor.r, 0.0, 1.0);
    float flashBoost = clamp(vColor.g, 0.0, 1.0);
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

    const int STEPS = 24;
    float stepSize = maxTravel / float(STEPS);
    float jitter = ign(gl_FragCoord.xy, fract(GameTime * 17.0) * 32.0);
    float travel = stepSize * jitter;
    float seedShift = isBlack ? 17.3 : 3.9;

    vec3 lightDir = normalize(vec3(0.45, 0.85, 0.25));
    vec4 sum = vec4(0.0);

    for (int i = 0; i < STEPS; i++) {
        if (sum.a > 0.96 || travel >= maxTravel) break;
        vec3 p = pStart + rd * travel;

        float heat, billow;
        float den = evalPyroField(p, progress, seedShift, heat, billow);

        if (den > 0.015) {
            // Directional light self-shadowing across neighboring Worley billows
            float dummyH, dummyB;
            float denLit = evalPyroField(p + lightDir * 0.11, progress, seedShift, dummyH, dummyB);
            float shadow = clamp(0.24 + (den - denLit) * 1.45 + clamp(p.y * 0.35, -0.10, 0.35), 0.12, 1.18);
            float internalLit = clamp(exp(-length(p) * 2.3) * (1.25 - 0.88 * progress), 0.0, 1.0);

            vec3 sampleRgb;
            if (isBlack) {
                vec3 voidSoot = vec3(0.008, 0.005, 0.016);
                vec3 darkPlum = vec3(0.12, 0.04, 0.22) * shadow + vec3(0.55, 0.14, 0.90) * internalLit * 0.65;
                vec3 electricViolet = vec3(0.78, 0.22, 1.05) * (1.25 + 0.55 * flashBoost);
                vec3 whiteVoid = vec3(1.22, 0.88, 1.52) * (1.20 + 0.65 * flashBoost);

                vec3 smokeCol = mix(voidSoot, darkPlum, clamp(shadow * 0.72, 0.0, 1.0));
                sampleRgb = mix(smokeCol, electricViolet, smoothstep(0.22, 0.82, heat));
                sampleRgb = mix(sampleRgb, whiteVoid, smoothstep(0.82, 1.58, heat));
            } else {
                vec3 charcoalAsh = vec3(0.05, 0.07, 0.11);
                vec3 sunlitSmoke = vec3(0.32, 0.38, 0.50) * shadow + vec3(0.22, 0.75, 1.05) * internalLit * 0.72;
                vec3 cyanPlasma = vec3(0.18, 0.84, 1.15) * (1.25 + 0.55 * flashBoost);
                vec3 hotWhite = vec3(1.28, 1.38, 1.55) * (1.20 + 0.70 * flashBoost);

                vec3 smokeCol = mix(charcoalAsh, sunlitSmoke, clamp(shadow * 0.75, 0.0, 1.0));
                sampleRgb = mix(smokeCol, cyanPlasma, smoothstep(0.22, 0.80, heat));
                sampleRgb = mix(sampleRgb, hotWhite, smoothstep(0.80, 1.52, heat));
            }

            float nearEyeFade = isInside ? smoothstep(0.05, 0.26, travel) : 1.0;
            float sampleAlpha = clamp(den * nearEyeFade * stepSize * mix(7.4, 4.4, smoothstep(0.35, 1.0, progress)), 0.0, 0.96);
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
