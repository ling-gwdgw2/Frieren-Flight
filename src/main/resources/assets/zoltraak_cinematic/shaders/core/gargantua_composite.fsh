#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// Pass 4: Analytic World Lensing, Starfield Lensing, Anamorphic Flares & Filmic Tonemapping

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D CosmicSampler;
uniform sampler2D HdrSampler;
uniform sampler2D BloomSampler;

in vec2 vUv;
out vec4 fragColor;

uniform mat4 InverseProjectionMat;
uniform mat4 InvViewRotMat;
uniform vec4 HoleCentre;
uniform vec4 SpinAxis;
uniform vec4 HoleState;
uniform vec4 DiskShape;
uniform vec4 DiskU;

const float SKY_DEPTH_THRESHOLD = 0.9999;
const float TWO_PI = 6.283185307;
const float PI = 3.141592654;
const float PLANCK_C2_UM_K = 14387.77;

const vec3 INV_LAMBDA_5 = vec3(9.67164, 22.17820, 52.87883);
const vec3 LAMBDA_UM = vec3(0.635, 0.538, 0.452);
const vec3 DAYLIGHT_WHITE_RAW = vec3(0.25727, 0.30106, 0.31417);

vec3 saturateVec3(vec3 x) {
    return clamp(x, vec3(0.0), vec3(1.0));
}

// Standard uniform cubic B-spline basis weights
vec4 cubic(float x) {
    float x2 = x * x;
    float x3 = x2 * x;
    vec4 w;
    w.x =   -x3 + 3.0 * x2 - 3.0 * x + 1.0;
    w.y =  3.0 * x3 - 6.0 * x2       + 4.0;
    w.z = -3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0;
    w.w =  x3;
    return w / 6.0;
}

vec3 bicubicBloom(sampler2D tex, vec2 coord) {
    vec2 res = vec2(textureSize(tex, 0));
    vec2 invRes = 1.0 / max(res, vec2(1.0));
    coord = clamp(coord, 0.001, 0.999) * res;

    float fx = fract(coord.x);
    float fy = fract(coord.y);
    coord.x -= fx;
    coord.y -= fy;

    fx -= 0.5;
    fy -= 0.5;

    vec4 xcubic = cubic(fx);
    vec4 ycubic = cubic(fy);

    vec4 c = vec4(coord.x - 0.5, coord.x + 1.5, coord.y - 0.5, coord.y + 1.5);
    vec4 s = vec4(xcubic.x + xcubic.y, xcubic.z + xcubic.w, ycubic.x + ycubic.y, ycubic.z + ycubic.w);
    vec4 offset = c + vec4(xcubic.y, xcubic.w, ycubic.y, ycubic.w) / max(s, vec4(1.0e-5));

    vec3 sample0 = texture(tex, clamp(vec2(offset.x, offset.z) * invRes, 0.0005, 0.9995)).rgb;
    vec3 sample1 = texture(tex, clamp(vec2(offset.y, offset.z) * invRes, 0.0005, 0.9995)).rgb;
    vec3 sample2 = texture(tex, clamp(vec2(offset.x, offset.w) * invRes, 0.0005, 0.9995)).rgb;
    vec3 sample3 = texture(tex, clamp(vec2(offset.y, offset.w) * invRes, 0.0005, 0.9995)).rgb;

    float sx = s.x / max(s.x + s.y, 1.0e-5);
    float sy = s.z / max(s.z + s.w, 1.0e-5);

    return mix(mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);
}

vec3 blackbodyPlanckSpectrum(float kelvin) {
    float t = max(kelvin, 950.0);
    vec3 x = clamp(PLANCK_C2_UM_K / (LAMBDA_UM * t), vec3(0.01), vec3(72.0));
    vec3 raw = INV_LAMBDA_5 / max(exp(x) - 1.0, vec3(1.0e-5));
    vec3 bal = raw / DAYLIGHT_WHITE_RAW;
    float peak = max(max(bal.r, bal.g), max(bal.b, 1.0e-5));
    return clamp(bal / peak, 0.0, 1.0);
}

vec3 pcgCellHash3(ivec3 cell) {
    uvec3 v = uvec3(cell + ivec3(16384)) ^ uvec3(0x9E3779B9u, 0x85EBCA6Bu, 0xC2B2AE35u);
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return vec3(v) * (1.0 / 4294967296.0);
}

vec3 sampleStarField(vec3 dir, float gridScale, float density, float timeSec, float galWeight) {
    vec3 p = dir * gridScale;
    ivec3 cell = ivec3(floor(p));
    vec3 f = p - vec3(cell);
    vec3 accum = vec3(0.0);
    for (int ox = -1; ox <= 1; ox++) {
        for (int oy = -1; oy <= 1; oy++) {
            for (int oz = -1; oz <= 1; oz++) {
                ivec3 nc = cell + ivec3(ox, oy, oz);
                vec3 h = pcgCellHash3(nc);
                float chance = density * mix(0.35, 1.15, galWeight);
                if (h.x > chance) continue;
                vec3 starPos = vec3(ox, oy, oz) + mix(vec3(0.18), vec3(0.82), h.yzx);
                float d = length(starPos - f);
                float lum = pow(clamp(h.y, 1.0e-4, 1.0), 2.4);
                float core = exp(-d * d * (95.0 - 45.0 * lum));
                float halo = exp(-d * (8.5 - 3.5 * lum)) * 0.16;
                float twinkle = 0.88 + 0.12 * sin(timeSec * (1.2 + 2.5 * h.z) + h.x * 80.0);
                float kelvin = mix(3600.0, 9200.0, h.z * h.z);
                accum += blackbodyPlanckSpectrum(kelvin) * (core + halo) * (0.28 + 1.85 * lum) * twinkle;
            }
        }
    }
    return accum;
}

vec3 sampleLensedStars(vec3 dir, float timeSec, float bendWeight, float baseMipLod) {
    float c1 = 0.891, s1 = 0.454;
    vec3 g1 = vec3(dir.x, dir.y * c1 - dir.z * s1, dir.y * s1 + dir.z * c1);
    float c2 = 0.961, s2 = 0.276;
    vec3 gal = normalize(vec3(g1.x * c2 - g1.y * s2, g1.x * s2 + g1.y * c2, g1.z));

    float galX = (abs(gal.x) < 1.0e-5 && abs(gal.z) < 1.0e-5) ? 1.0e-5 : gal.x;
    float u = atan(gal.z, galX) / TWO_PI + 0.5;
    float v = acos(clamp(gal.y, -1.0, 1.0)) / PI;
    vec3 texRaw = textureLod(CosmicSampler, vec2(u, v), baseMipLod).rgb;
    vec3 lin = pow(max(texRaw, vec3(1.0e-6)), vec3(2.2));

    float bandArg = gal.y * 2.8;
    float bandWeight = exp(-(bandArg * bandArg));
    vec3 stars = sampleStarField(gal, 138.0, 0.095, timeSec, bandWeight) * 0.65;
    stars += sampleStarField(gal + vec3(0.37, 0.71, 0.19), 296.0, 0.14, timeSec * 0.82, bandWeight) * 0.42;
    stars += lin * vec3(1.15, 0.90, 0.68) * 0.35;

    float lensAmplify = bendWeight * 1.15 + pow(max(bendWeight, 1.0e-5), 2.2) * 0.85;
    stars *= lensAmplify;
    return stars / (1.0 + stars * 0.5);
}

vec3 computeWorldTerrainGlow(
    vec3 rayDir, float sceneDistance, bool isActualSky, vec3 centre, vec3 axis,
    float rg, float uScale, float openFactor, float crit, float blastProg, bool isBlasting, float sedov, float diskBrightness
) {
    vec3 pos = rayDir * min(sceneDistance, 300.0);
    vec3 dX = dFdx(pos);
    vec3 dY = dFdy(pos);
    if (dot(dX, dX) > 225.0 || dot(dY, dY) > 225.0) {
        return vec3(0.0);
    }
    vec3 grad = cross(dX, dY);
    float gradLen = length(grad);
    if (isActualSky || sceneDistance >= 210.0 || gradLen <= 1.0e-6) {
        return vec3(0.0);
    }

    vec3 toHole = centre - pos;
    float dist = length(toHole);
    if (!isBlasting && dist >= 100.0) {
        return vec3(0.0);
    }

    vec3 surfN = grad / gradLen;
    if (dot(surfN, rayDir) > 0.0) {
        surfN = -surfN;
    }

    vec3 lightDir = toHole / max(dist, 0.01);

    // Point-Light Lambert: directional cosine shading so surfaces facing away stay naturally shadowed
    float rawDot = dot(surfN, lightDir);
    float nDotL = max(rawDot, 0.0);
    float lambert = pow(nDotL, 1.05) * 0.94 + 0.06 * smoothstep(-0.15, 0.25, rawDot);

    vec3 halfVec = normalize(lightDir - rayDir);
    float spec = pow(max(dot(surfN, halfVec), 0.0), 28.0) * nDotL * 0.28;

    float eqAlign = 1.0 - abs(dot(lightDir, axis));
    vec3 diskCoreGold = vec3(1.12, 0.86, 0.58);
    vec3 outerFieryAmber = vec3(1.15, 0.62, 0.30);
    vec3 tint = mix(outerFieryAmber, diskCoreGold, smoothstep(0.25, 0.85, eqAlign));

    // 100-block physical point-light falloff
    float rangeMask = 1.0 - smoothstep(68.0, 100.0, dist);
    float rNorm = dist / max(rg * 6.5, 3.0);
    float atten = (1.0 / (1.0 + rNorm * rNorm * 1.15)) * rangeMask * (1.0 - smoothstep(65.0, 210.0, sceneDistance));

    if (isBlasting) {
        float blastRange = 1.0 - smoothstep(85.0, 125.0, dist);
        float waveWorldR = max((0.42 + 12.2 * sedov) * uScale, rg * 1.0);
        float ringWidth = mix(2.2, 6.8, sedov);
        float waveRing = exp(-pow((dist - waveWorldR) / max(ringWidth, 1.0), 2.0)) * blastRange;
        float shockDecay = 1.0 - smoothstep(0.36, 0.80, blastProg);
        vec3 shockTint = mix(vec3(1.42, 1.16, 0.80), vec3(1.28, 0.54, 0.22), smoothstep(0.10, 0.68, blastProg));
        tint = mix(tint, shockTint, clamp(waveRing * 0.85 + 0.35, 0.0, 1.0));
        float blastPower = openFactor * (0.65 + 1.35 * waveRing * shockDecay) * shockDecay * diskBrightness;
        return tint * ((lambert * 0.68 + spec) * max(atten * (1.0 - smoothstep(0.0, 0.72, blastProg)), waveRing * 0.65) * blastPower);
    }

    float power = openFactor * (1.0 + crit * 0.55) * diskBrightness;
    return tint * ((lambert * 0.68 + spec) * atten * power);
}

// Soft tonemapped linear light integration prevents post-tonemap yellow clipping on bright blocks (e.g. End Stone)
vec3 applySoftTerrainLight(vec3 baseSrgb, vec3 glow) {
    if (dot(glow, glow) <= 1.0e-7) {
        return baseSrgb;
    }
    vec3 baseLin = pow(max(baseSrgb, vec3(0.0)), vec3(2.2));
    vec3 litAlbedo = baseLin * (vec3(1.0) + glow * 1.85) + glow * (0.045 + 0.12 * baseLin);
    vec3 delta = max(litAlbedo - baseLin, vec3(0.0));
    vec3 compressedDelta = delta / (vec3(1.0) + delta * 0.85);
    vec3 outLin = baseLin + compressedDelta * (vec3(1.0) - baseLin * 0.38);
    return clamp(pow(max(outLin, vec3(0.0)), vec3(1.0 / 2.2)), vec3(0.0), vec3(1.0));
}

void main() {
    float rg = max(HoleCentre.w, 0.0001);
    vec3 sceneColour = texture(SceneSampler, vUv).rgb;

    float openFactor = clamp(HoleState.x, 0.0, 1.0);
    if (openFactor <= 0.0005) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    vec4 clip = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 1.0e-6) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    vec3 rayDir = normalize(eye.xyz / eye.w);
    float angularStep = max(length(dFdx(rayDir)), length(dFdy(rayDir)));
    float baseMipLod = clamp(log2(max(angularStep * 326.0, 1.0)), 0.0, 4.0);

    vec3 centre = HoleCentre.xyz;
    float holeDistance = length(centre);
    float along = dot(centre, rayDir);
    vec3 impactVec = rayDir * along - centre;
    float impact = length(impactVec);

    bool isBlasting = (HoleState.y > 1.002);
    float blastProg = isBlasting ? clamp((HoleState.y - 1.005) / 0.995, 0.0, 1.0) : 0.0;
    float crit = isBlasting ? 1.0 : clamp(HoleState.y, 0.0, 1.0);
    float sedov = isBlasting ? pow(max(blastProg, 1.0e-4), 0.54) : 0.0;
    float timeSec = HoleState.z;
    float swallowed = clamp(HoleState.w, 0.0, 10.0);

    float sceneDistance = 1.0e9;
    float depth = texture(DepthSampler, vUv).r;
    if (depth > 0.0001 && depth < SKY_DEPTH_THRESHOLD) {
        vec4 sceneClip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 sceneEye = InverseProjectionMat * sceneClip;
        if (abs(sceneEye.w) > 1.0e-6 && (sceneEye.z / sceneEye.w) < 0.0) {
            sceneDistance = length(sceneEye.xyz / sceneEye.w);
        }
    }

    bool isActualSky = (depth >= SKY_DEPTH_THRESHOLD) || (sceneDistance >= 350.0);
    float uScale = max(rg * 1.65, 0.05);
    float impactR = impact / uScale;

    vec3 axis = normalize(SpinAxis.xyz + DiskU.xyz * 1.0e-6);
    float lensMult = (SpinAxis.w >= 0.05) ? 1.0 : 0.0;
    float aberrationMode = SpinAxis.w;

    float worldGlowFlag = DiskShape.x;
    float diskSizeCfg = DiskShape.y;
    float lensFlareFlag = DiskShape.z;
    float diskBrightness = clamp(DiskShape.w, 0.0, 2.0);
    float cinematicWeight = clamp(DiskU.w, 0.0, 1.0);

    vec3 rawScene = sceneColour;
    vec3 terrainGlow = vec3(0.0);
    if (worldGlowFlag > 0.5) {
        terrainGlow = computeWorldTerrainGlow(
            rayDir, sceneDistance, isActualSky, centre, axis, rg, uScale, openFactor, crit, blastProg, isBlasting, sedov, diskBrightness
        );
    }

    float massScale = 1.0 + min(swallowed * 0.014, 0.22);
    float baseScale = mix(0.35, 1.0, openFactor) * massScale;
    float discScale = clamp(diskSizeCfg / 22.0, 0.45, 1.80) * baseScale;
    float horizonR = 0.48 * baseScale;
    float photonR = 0.72 * baseScale;
    float boundRadius = max(7.5, 6.2 * discScale + 1.5);
    float shockR = 0.42 * baseScale + 12.2 * sedov;
    float holeDistUnits = holeDistance / uScale;

    if (!isBlasting) {
        if (along <= 0.0 || impactR >= boundRadius * 1.35) {
            vec3 outCol = (worldGlowFlag > 0.5) ? applySoftTerrainLight(rawScene, terrainGlow) : rawScene;
            if (crit > 0.01) {
                float vigDist = smoothstep(0.45, 1.38, length(vUv * 2.0 - 1.0));
                outCol *= (1.0 - 0.34 * crit * crit * vigDist);
            }
            fragColor = vec4(outCol, 1.0);
            return;
        }

        bool foregroundOccluded = (!isActualSky && sceneDistance < (along - boundRadius * uScale));
        if (foregroundOccluded) {
            vec3 outCol = (worldGlowFlag > 0.5) ? applySoftTerrainLight(rawScene, terrainGlow) : rawScene;
            if (crit > 0.01) {
                float vigDist = smoothstep(0.45, 1.38, length(vUv * 2.0 - 1.0));
                outCol *= (1.0 - 0.34 * crit * crit * vigDist);
            }
            fragColor = vec4(outCol, 1.0);
            return;
        }
    } else if (blastProg >= 0.045) {
        float blastCullBound = max(boundRadius, shockR + max(4.5, 0.14 * holeDistUnits));
        if (holeDistUnits > blastCullBound) {
            bool outOfBlast = (along <= 0.0 || impactR >= blastCullBound * 1.15);
            bool occludedInFront = (!isActualSky && along > 0.0 && sceneDistance < (along - blastCullBound * uScale));
            if (outOfBlast || occludedInFront) {
                vec3 outCol = (worldGlowFlag > 0.5) ? applySoftTerrainLight(rawScene, terrainGlow) : rawScene;
                fragColor = vec4(outCol, 1.0);
                return;
            }
        }
    }

    vec4 hdrSample = texture(HdrSampler, vUv);
    vec3 hdrColor = hdrSample.rgb;
    float transmittance = clamp(hdrSample.a, 0.0, 1.0);

    // Analytic Schwarzschild gravitational lensing + Relativistic gravity-snap shockwave distortion
    float edgeFade = smoothstep(boundRadius * 0.96, boundRadius * 0.24, impactR);
    float rSafe = max(impactR, photonR * 0.92);
    float gravWeight = isBlasting ? (1.0 - smoothstep(0.0, 0.14, blastProg)) * 1.45 : (1.0 + 0.55 * crit * crit);
    float bendAngle = (1.35 * horizonR / (rSafe * rSafe + 0.08)
                     + 0.48 * (horizonR * horizonR) / max(rSafe * rSafe * rSafe, 0.02))
                     * edgeFade * openFactor * lensMult * gravWeight;
    vec3 radialUnit = (impact > 1.0e-5) ? (impactVec / impact) : vec3(1.0, 0.0, 0.0);
    vec3 lensedD = normalize(rayDir - radialUnit * bendAngle);

    float aspect = InverseProjectionMat[0][0] / max(InverseProjectionMat[1][1], 1.0e-5);
    vec2 lensScale = vec2(1.0 / max(aspect, 0.1), 1.0);
    bool hitTerrainInVolume = (!isActualSky && sceneDistance < (along + (isBlasting ? shockR : boundRadius) * uScale));

    vec3 lensedBg;
    if (hitTerrainInVolume && sceneDistance < (isBlasting ? along : holeDistance)) {
        lensedBg = (worldGlowFlag > 0.5) ? applySoftTerrainLight(rawScene, terrainGlow) : rawScene;
    } else {
        float warpStrength = hitTerrainInVolume ? 0.014 : 0.026;
        vec2 lensOffset = (lensedD.xy - rayDir.xy) * lensScale * (warpStrength * openFactor * lensMult);

        if (isBlasting && along > 0.0) {
            float waveWidth = mix(0.32, 1.65, sedov);
            float waveDelta = (impactR - shockR) / max(waveWidth, 0.16);
            float waveEnv = smoothstep(0.005, 0.04, blastProg) * (1.0 - smoothstep(0.30, 0.80, blastProg));
            float gravWave = -waveDelta * exp(-waveDelta * waveDelta * 1.45) * waveEnv * openFactor * lensMult;
            lensOffset += radialUnit.xy * lensScale * (gravWave * 0.042);
        }

        vec2 warpedUv = clamp(vUv + lensOffset, 0.001, 0.999);

        float sampleDepth = texture(DepthSampler, warpedUv).r;
        if (sampleDepth < SKY_DEPTH_THRESHOLD) {
            vec4 sClip = vec4(warpedUv * 2.0 - 1.0, sampleDepth * 2.0 - 1.0, 1.0);
            vec4 sEye = InverseProjectionMat * sClip;
            if (abs(sEye.w) > 1.0e-6 && length(sEye.xyz / sEye.w) < max(holeDistance - boundRadius * uScale * 0.5, 2.0)) {
                warpedUv = vUv;
                lensOffset = vec2(0.0);
            }
        }

        vec3 bg;
        bool hasLensWarp = dot(lensOffset, lensOffset) > 1.0e-12;
        bool useBlastDispersion = isBlasting && dot(lensOffset, lensOffset) > 1.0e-7;
        if ((aberrationMode > 1.5 || useBlastDispersion) && hasLensWarp) {
            bg.r = texture(SceneSampler, clamp(vUv + lensOffset * 0.88, 0.001, 0.999)).r;
            bg.g = texture(SceneSampler, warpedUv).g;
            bg.b = texture(SceneSampler, clamp(vUv + lensOffset * 1.12, 0.001, 0.999)).b;
        } else if (hasLensWarp) {
            bg = texture(SceneSampler, warpedUv).rgb;
        } else {
            bg = rawScene;
        }

        if (worldGlowFlag > 0.5) {
            bg = applySoftTerrainLight(bg, terrainGlow);
            if (isActualSky) {
                float bendAmount = length(lensedD - rayDir);
                float bendWeight = smoothstep(0.015, 0.28, bendAmount);
                vec3 worldD = (InvViewRotMat * vec4(lensedD, 0.0)).xyz;
                vec3 lensedStars = sampleLensedStars(worldD, timeSec, bendWeight, baseMipLod);
                float starMask = smoothstep(boundRadius * 0.65, boundRadius * 0.22, impactR) * smoothstep(0.03, 0.22, bendAmount) * (isBlasting ? (1.0 - smoothstep(0.0, 0.20, blastProg)) : 1.0);
                bg += lensedStars * starMask * 0.55;
            }
        }
        lensedBg = bg;
    }

    vec3 behindEffect = lensedBg * transmittance;

    // Reconstruct multi-octave bloom via B-spline bicubic filter
    vec3 bloom = bicubicBloom(BloomSampler, vUv);

    // Protect bare event horizon shadow interior while alive, then lift mask seamlessly at core annihilation
    float shadowMaskAlive = isBlasting ? (1.0 - smoothstep(0.0, 0.055, blastProg)) : 1.0;
    float hdrPeak = max(hdrColor.r, max(hdrColor.g, hdrColor.b));
    float bareShadowMask = clamp(1.0 - transmittance, 0.0, 1.0) * smoothstep(0.06, 0.002, hdrPeak) * shadowMaskAlive;
    float shadowRim = smoothstep(0.20 * baseScale, 0.92 * baseScale, impactR);
    bloom *= mix(1.0, shadowRim * shadowRim * 0.22, bareShadowMask);

    // Optional anamorphic equatorial flare streak outside the shadow (intensifies cleanly during core collapse snap)
    if (lensFlareFlag > 0.5 && along > 0.0) {
        vec2 diskDir2D = normalize(vec2(dot(DiskU.xyz, vec3(1.0, 0.0, 0.0)), dot(DiskU.xyz, vec3(0.0, 1.0, 0.0))) + vec2(1.0e-5, 0.0));
        vec2 relVec = (impactVec.xy / max(uScale, 0.05));
        float alongDisk = abs(dot(relVec, diskDir2D));
        float perpDisk = abs(dot(relVec, vec2(-diskDir2D.y, diskDir2D.x)));
        float outerOnly = mix(smoothstep(0.85 * baseScale, 1.35 * baseScale, impactR), 1.0, isBlasting ? smoothstep(0.0, 0.04, blastProg) : 0.0);
        float flareScale = isBlasting
                ? (1.65 * exp(-pow(blastProg / 0.14, 2.0)) * (1.0 - smoothstep(0.18, 0.45, blastProg)))
                : (1.0 + 0.35 * crit);
        float flareExtend = isBlasting ? max(discScale, shockR * 0.38) : discScale;
        float streakProfile = exp(-perpDisk * 6.5 / max(baseScale, 0.5)) * exp(-alongDisk * 0.55 / max(flareExtend, 0.5)) * outerOnly;
        bloom += vec3(1.25, 0.96, 0.68) * (streakProfile * 0.35 * flareScale * openFactor * diskBrightness * (1.0 + 0.35 * cinematicWeight));
    }

    // Filmic tone mapping & warm channel color grading pipeline
    vec3 color = (hdrColor + bloom * 0.075) * 2.0;

    // Power-Reinhard (p = 1.5)
    color = pow(max(color, vec3(0.0)), vec3(1.5));
    color = color / (vec3(1.0) + color);
    color = pow(max(color, vec3(0.0)), vec3(1.0 / 1.5));

    // Hermite S-curve contrast
    color = mix(color, color * color * (3.0 - 2.0 * color), vec3(1.0));

    // Warm channel power grading (preserves fiery amber-orange outer dust & white-gold core without turning dim haze purple)
    color = pow(max(color, vec3(0.0)), vec3(1.02, 1.12, 1.28));
    vec3 preLift = saturateVec3(color * 1.02);

    // Smooth filmic gamma lift that keeps faint outer haze transparent against the Minecraft sky (no circular fog dome)
    vec3 lifted = pow(max(preLift, vec3(1.0e-6)), vec3(0.55));
    float peakPre = max(preLift.r, max(preLift.g, preLift.b));
    float boundaryFeather = smoothstep(boundRadius * 1.08, boundRadius * 0.58, impactR);
    if (isBlasting) {
        boundaryFeather = mix(boundaryFeather, 1.0, smoothstep(0.0, 0.03, blastProg));
    }
    float toeMask = smoothstep(0.0005, 0.045, peakPre);
    vec3 gradedHole = mix(preLift, lifted, toeMask) * smoothstep(0.0, 0.0012, peakPre) * boundaryFeather;

    // Attenuate background behind bright plasma & bloom halo, then composite cleanly
    float holePresence = clamp(max(gradedHole.r, max(gradedHole.g, gradedHole.b)), 0.0, 1.0);
    vec3 composite = behindEffect * (1.0 - holePresence * 0.85) + gradedHole;

    // Act 3 optical periphery compression (radial, never boxy GUI stripes)
    if (!isBlasting && crit > 0.01) {
        float vigDist = smoothstep(0.45, 1.38, length(vUv * 2.0 - 1.0));
        composite *= (1.0 - 0.34 * crit * crit * vigDist);
    }

    // Act 4 brief 4-frame Impact Frame snap (high-contrast optical glint at detonation core, fades before obscuring wave)
    if (isBlasting && blastProg < 0.038) {
        float impT = clamp(blastProg / 0.038, 0.0, 1.0);
        float impEnv = sin(impT * 3.14159265) * pow(1.0 - impT, 0.65);
        float radialGlint = (along > 0.0) ? exp(-pow(impactR / 1.4, 2.0)) : 0.04;
        composite = composite * (1.0 + impEnv * 0.22) + vec3(0.42, 0.46, 0.56) * (impEnv * radialGlint * 0.35);
    }

    composite = clamp(composite, vec3(0.0), vec3(1.0));

    if (openFactor >= 0.9999) {
        fragColor = vec4(composite, 1.0);
    } else {
        vec3 finalScene = (worldGlowFlag > 0.5) ? applySoftTerrainLight(rawScene, terrainGlow) : rawScene;
        fragColor = vec4(mix(finalScene, composite, openFactor), 1.0);
    }
}
