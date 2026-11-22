#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// Phase 3 Anime Cinema Post-Processor for Explosion Magic:
// - Dynamic World Terrain Firelight + Sweeping Shockwave Ring (DepthSampler + dFdx/dFdy normals)
// - Atmospheric Sky Eclipse (Chant/Strike Tension) & Horizon Mie Forward-Scattering Glow
// - Radial Air-Compression Shockwave Screen Refraction & Prismatic RGB Chromatic Aberration
// - Screen-Space Radial God Rays & High-Contrast Sakuga Impact Frames

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

in vec2 vUv;
out vec4 fragColor;

uniform mat4 InverseProjectionMat;
uniform vec4 BlastOrigin;  // xyz = eye-space detonation pos, w = visualRadius
uniform vec4 SkyApex;      // xyz = eye-space sky ring apex, w = chargeIntensity (0..1, >0 while charging)
uniform vec4 BlastTiming;  // x = normTime (0..220), y = dt (t - 28), z = blastProgress (0..1, <0 if not detonated), w = bloomScale
uniform vec4 BlastScreen;  // xy = projected blast UV, z = inFrontWeight (0..1), w = flashStrength
uniform vec4 BlastFlags;   // x = worldGlow (0/1), y = chromaticAberration (0/1), z = impactFrames (0/1), w = distFactor (0..1)

const float SKY_DEPTH_THRESHOLD = 0.9999;

vec3 applySoftTerrainLight(vec3 baseSrgb, vec3 glow) {
    if (dot(glow, glow) <= 1.0e-7) {
        return baseSrgb;
    }
    // Protect already-emissive bright fireball/beam pixels from washing out
    float peakCh = max(baseSrgb.r, max(baseSrgb.g, baseSrgb.b));
    float emissiveProtect = 1.0 - smoothstep(0.72, 1.12, peakCh);
    vec3 safeGlow = glow * (0.22 + 0.78 * emissiveProtect);

    vec3 baseLin = pow(max(baseSrgb, vec3(0.0)), vec3(2.2));
    vec3 litAlbedo = baseLin * (vec3(1.0) + safeGlow * 2.15) + safeGlow * (0.045 + 0.14 * baseLin);
    vec3 delta = max(litAlbedo - baseLin, vec3(0.0));
    vec3 compressedDelta = delta / (vec3(1.0) + delta * 0.82);
    vec3 outLin = baseLin + compressedDelta * (vec3(1.0) - baseLin * 0.35);
    return clamp(pow(max(outLin, vec3(0.0)), vec3(1.0 / 2.2)), vec3(0.0), vec3(1.0));
}

vec3 computeExplosionTerrainGlow(
    vec3 rayDir, vec3 pos, vec3 dX, vec3 dY, float sceneDist, bool isActualSky, vec3 blastCenter, vec3 skyApex, vec3 upAxis,
    float visualR, float chargeInt, float normT, float dt, float blastProg, float bloomScale
) {
    if (isActualSky || sceneDist >= 220.0 || dot(dX, dX) > 225.0 || dot(dY, dY) > 225.0) {
        return vec3(0.0);
    }
    vec3 grad = cross(dX, dY);
    float gradLen = length(grad);
    if (gradLen <= 1.0e-6) {
        return vec3(0.0);
    }
    vec3 surfN = grad / gradLen;
    if (dot(surfN, rayDir) > 0.0) {
        surfN = -surfN;
    }

    float viewFade = 1.0 - smoothstep(95.0, 220.0, sceneDist);
    vec3 totalGlow = vec3(0.0);

    // 1. Sky Vortex Ring / Pillar Down-Light (fades out together with the 8 sky rings & vertical pillar at t = 26..50)
    float skyPillarFade = (normT >= 0.0 && normT < 50.0) ? (1.0 - smoothstep(26.0, 50.0, normT)) * 0.92 : 0.0;
    float skyActivity = max(chargeInt * 0.65, skyPillarFade);
    if (skyActivity > 0.01) {
        vec3 skyLightPos = mix(blastCenter, skyApex, 0.58);
        vec3 toSky = skyLightPos - pos;
        float distSky = length(toSky);
        float skyRange = 1.0 - smoothstep(visualR * 2.4, visualR * 4.8, distSky);
        if (skyRange > 0.005) {
            vec3 lDir = toSky / max(distSky, 0.01);
            float nDotL = max(dot(surfN, lDir), 0.0);
            float rNorm = distSky / max(visualR * 1.45, 6.0);
            float atten = (1.0 / (1.0 + rNorm * rNorm * 0.95)) * skyRange;
            totalGlow += vec3(1.12, 0.48, 0.10) * ((nDotL * 0.85 + 0.15) * atten * skyActivity * 0.85);
        }
    }

    // 2. Pre-Detonation Pillar Impact & Full Detonation Fireball + Sweeping Shockwave Light Ring
    if (normT >= 17.5 && blastProg < 0.96) {
        float fireLiftFrac = blastProg >= 0.0 ? (0.36 + 0.28 * blastProg) : 0.14;
        vec3 fireLightPos = blastCenter + upAxis * (visualR * fireLiftFrac);
        vec3 toFire = fireLightPos - pos;
        float distFire = length(toFire);
        float maxRange = max(96.0, visualR * 5.2);
        float rangeMask = 1.0 - smoothstep(maxRange * 0.55, maxRange, distFire);

        if (rangeMask > 0.005) {
            vec3 lDir = toFire / max(distFire, 0.01);
            float rawDot = dot(surfN, lDir);
            float nDotL = max(rawDot, 0.0);
            float lambert = pow(nDotL, 1.05) * 0.92 + 0.08 * smoothstep(-0.18, 0.25, rawDot);
            vec3 halfVec = normalize(lDir - rayDir);
            float spec = pow(max(dot(surfN, halfVec), 0.0), 24.0) * nDotL * 0.30;

            float rNorm = distFire / max(visualR * 1.35, 6.0);
            float atten = (1.0 / (1.0 + rNorm * rNorm * 0.85)) * rangeMask;

            if (dt < 0.0) {
                // Pre-detonation ground compression glow right before boom (t = 17.5 .. 28)
                float preP = smoothstep(17.5, 28.0, normT);
                vec3 preTint = vec3(1.24, 0.84, 0.32);
                totalGlow += preTint * ((lambert * 0.78 + spec) * atten * preP * 1.15);
            } else {
                // Cataclysmic detonation firelight + sweeping terrain shockwave ring
                float fireDecay = (1.0 - smoothstep(0.18, 0.88, blastProg));
                float flashBoost = exp(-dt * 0.12) * 0.95;
                float sedov = pow(clamp(dt / 36.0, 0.0, 1.0), 0.54);
                float waveWorldR = visualR * (0.45 + 3.25 * sedov);
                float ringWidth = max(2.6, visualR * mix(0.14, 0.36, sedov));
                float waveRing = exp(-pow((distFire - waveWorldR) / ringWidth, 2.0)) * (1.0 - smoothstep(18.0, 44.0, dt)) * rangeMask;

                vec3 fireTint = mix(vec3(1.32, 0.96, 0.42), vec3(1.18, 0.38, 0.08), smoothstep(0.06, 0.56, blastProg));
                vec3 waveTint = vec3(1.42, 1.18, 0.72);
                vec3 combTint = mix(fireTint, waveTint, clamp(waveRing * 0.80, 0.0, 1.0));

                float power = (0.95 + flashBoost) * fireDecay + waveRing * 1.25;
                totalGlow += combTint * ((lambert * 0.76 + spec) * max(atten * fireDecay, waveRing * 0.72) * power);
            }
        }
    }

    return totalGlow * viewFade * clamp(bloomScale, 0.25, 2.0);
}

void main() {
    vec3 rawScene = texture(SceneSampler, vUv).rgb;

    float visualR = max(BlastOrigin.w, 4.0);
    float chargeInt = clamp(SkyApex.w, 0.0, 1.0);
    float normT = BlastTiming.x;
    float dt = BlastTiming.y;
    float blastProg = BlastTiming.z;
    float bloomScale = clamp(BlastTiming.w, 0.0, 2.0);

    vec2 blastUv = BlastScreen.xy;
    float inFront = clamp(BlastScreen.z, 0.0, 1.0);
    float flashStrength = clamp(BlastScreen.w, 0.0, 1.0);

    float worldGlowFlag = BlastFlags.x;
    float caFlag = BlastFlags.y;
    float impactFlag = BlastFlags.z;
    float distFactor = clamp(BlastFlags.w, 0.0, 1.0);

    vec4 clip = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    float safeEyeW = abs(eye.w) > 1.0e-6 ? eye.w : 1.0;
    vec3 rayDir = normalize(eye.xyz / safeEyeW);

    float depth = texture(DepthSampler, vUv).r;
    float sceneDist = 1.0e9;
    if (depth > 0.0001 && depth < SKY_DEPTH_THRESHOLD) {
        vec4 sClip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 sEye = InverseProjectionMat * sClip;
        if (abs(sEye.w) > 1.0e-6 && (sEye.z / sEye.w) < 0.0) {
            sceneDist = length(sEye.xyz / sEye.w);
        }
    }
    bool isActualSky = (depth >= SKY_DEPTH_THRESHOLD) || (sceneDist >= 360.0);

    // Evaluate screen-space surface derivatives in uniform control flow before any per-pixel branches
    vec3 scenePos = rayDir * min(sceneDist, 260.0);
    vec3 sceneDx = dFdx(scenePos);
    vec3 sceneDy = dFdy(scenePos);

    if (abs(eye.w) < 1.0e-6) {
        fragColor = vec4(rawScene, 1.0);
        return;
    }

    vec3 blastCenter = BlastOrigin.xyz;
    vec3 skyApex = SkyApex.xyz;
    vec3 apexDelta = skyApex - blastCenter;
    vec3 upAxis = length(apexDelta) > 0.01 ? normalize(apexDelta) : vec3(0.0, 1.0, 0.0);

    float along = dot(blastCenter, rayDir);
    vec3 impactVec = rayDir * along - blastCenter;
    float impactDist = length(impactVec);
    float aspect = InverseProjectionMat[0][0] / max(InverseProjectionMat[1][1], 1.0e-5);
    vec2 aspectScale = vec2(1.0 / max(aspect, 0.1), 1.0);

    // 1. Radial Air-Compression Shockwave Screen Refraction + Prismatic Chromatic Aberration
    vec2 uvOffset = vec2(0.0);
    float shockCaWeight = 0.0;

    if (dt >= 0.0 && dt < 36.0 && along > 0.0) {
        float sp = clamp(dt / 36.0, 0.0, 1.0);
        float sedov = pow(sp, 0.56);
        float shockWorldR = visualR * (0.48 + 2.85 * sedov);
        float waveWidth = max(1.8, visualR * mix(0.14, 0.34, sedov));
        float waveDelta = (impactDist - shockWorldR) / waveWidth;
        float waveEnv = smoothstep(0.0, 0.06, sp) * (1.0 - smoothstep(0.26, 0.96, sp)) * distFactor;
        float waveForce = -waveDelta * exp(-waveDelta * waveDelta * 1.45) * waveEnv;

        bool behindShock = isActualSky || (sceneDist > max(along - shockWorldR * 1.05, 1.8));
        if (behindShock && abs(waveForce) > 1.0e-4) {
            vec3 radUnit = impactDist > 1.0e-4 ? (impactVec / impactDist) : vec3(1.0, 0.0, 0.0);
            uvOffset = radUnit.xy * aspectScale * (waveForce * 0.036);
            shockCaWeight = abs(waveForce);
        }
    }

    // Subtle screen-edge radial CA punch right on detonation
    vec2 centeredScreen = vUv - 0.5;
    float detBurstCa = (dt >= 0.0 && dt < 14.0) ? exp(-dt * 0.24) * distFactor * 0.0085 : 0.0;

    vec2 warpedUv = clamp(vUv + uvOffset, 0.001, 0.999);
    // Depth-guard prevents refracting foreground blocks in front of the blast
    if (dot(uvOffset, uvOffset) > 1.0e-9) {
        float wDepth = texture(DepthSampler, warpedUv).r;
        if (wDepth < SKY_DEPTH_THRESHOLD) {
            vec4 wClip = vec4(warpedUv * 2.0 - 1.0, wDepth * 2.0 - 1.0, 1.0);
            vec4 wEye = InverseProjectionMat * wClip;
            if (abs(wEye.w) > 1.0e-6 && length(wEye.xyz / wEye.w) < max(along - visualR * 2.2, 2.0)) {
                warpedUv = vUv;
                uvOffset = vec2(0.0);
            }
        }
    }

    vec3 color;
    if (caFlag > 0.5 && (shockCaWeight > 0.015 || detBurstCa > 0.0005)) {
        vec2 caVec = uvOffset * 0.32 + centeredScreen * detBurstCa;
        color.r = texture(SceneSampler, clamp(warpedUv - caVec, 0.001, 0.999)).r;
        color.g = texture(SceneSampler, warpedUv).g;
        color.b = texture(SceneSampler, clamp(warpedUv + caVec, 0.001, 0.999)).b;
    } else if (dot(uvOffset, uvOffset) > 1.0e-9) {
        color = texture(SceneSampler, warpedUv).rgb;
    } else {
        color = rawScene;
    }

    // 2. Dynamic World Terrain Firelight & Sweeping Shockwave Illumination
    if (worldGlowFlag > 0.5 && !isActualSky) {
        vec3 terrainGlow = computeExplosionTerrainGlow(
            rayDir, scenePos, sceneDx, sceneDy, sceneDist, isActualSky, blastCenter, skyApex, upAxis,
            visualR, chargeInt, normT, dt, blastProg, bloomScale
        );
        color = applySoftTerrainLight(color, terrainGlow);
    }

    // 3. Atmospheric Sky Eclipse (Chant / Pre-Strike) & Horizon Mie Forward-Scattering Glow
    if (isActualSky) {
        float skyLuma = dot(color, vec3(0.299, 0.587, 0.114));
        float darkSkyMask = 1.0 - smoothstep(0.26, 0.72, skyLuma);

        // Dramatic pre-detonation sky eclipse (makes golden sky rings blaze with high contrast)
        float eclipseCharge = smoothstep(0.32, 1.0, chargeInt) * 0.42;
        float eclipseStrike = (normT >= 0.0 && dt < 1.0) ? (0.42 + 0.18 * smoothstep(14.0, 28.0, normT)) : 0.0;
        float eclipseFade = (dt >= 0.0) ? (1.0 - smoothstep(0.0, 12.0, dt)) * 0.48 : 0.0;
        float eclipseAmt = clamp(max(eclipseCharge, max(eclipseStrike, eclipseFade)), 0.0, 0.62) * distFactor * darkSkyMask;
        if (eclipseAmt > 0.004) {
            vec3 eclipseCol = color * vec3(0.58, 0.42, 0.60) + vec3(0.06, 0.012, 0.025);
            color = mix(color, eclipseCol, eclipseAmt);
        }

        // Warm Mie forward-scattering golden-crimson horizon glow after detonation
        if (dt >= 0.0 && blastProg < 0.88) {
            vec3 scatterTarget = blastCenter + upAxis * (visualR * 0.72);
            float cosPhi = clamp(dot(rayDir, normalize(scatterTarget)), 0.0, 1.0);
            float mie = pow(cosPhi, 5.0) * 0.58 + pow(cosPhi, 18.0) * 0.42;
            float mieLife = smoothstep(0.0, 6.0, dt) * (1.0 - smoothstep(0.28, 0.88, blastProg));
            vec3 mieCol = mix(vec3(1.10, 0.44, 0.08), vec3(1.24, 0.86, 0.34), pow(cosPhi, 4.0));
            color += mieCol * (mie * mieLife * 0.34 * bloomScale * distFactor);
        }
    }

    // 4. Screen-Space Radial God Rays (12 taps streaming outward from blast core, dt = 0..22)
    if (dt >= 0.0 && dt < 22.0 && inFront > 0.02 && bloomScale > 0.05) {
        float rayEnv = smoothstep(0.0, 1.5, dt) * (1.0 - smoothstep(4.5, 22.0, dt)) * inFront * distFactor;
        vec2 deltaRay = (blastUv - vUv);
        float uvDist = length(deltaRay * vec2(aspect, 1.0));
        float rayReach = clamp(1.0 - uvDist * 0.68, 0.0, 1.0);
        if (rayEnv * rayReach > 0.008) {
            vec2 stepUv = deltaRay * (0.28 / 12.0);
            vec2 sampleUv = vUv;
            vec3 shaftAccum = vec3(0.0);
            float decay = 1.0;
            for (int i = 0; i < 12; i++) {
                sampleUv += stepUv;
                vec3 tap = texture(SceneSampler, clamp(sampleUv, 0.002, 0.998)).rgb;
                float tapLuma = max(tap.r, max(tap.g, tap.b));
                float brightPass = smoothstep(0.58, 1.0, tapLuma);
                shaftAccum += tap * brightPass * decay;
                decay *= 0.88;
            }
            shaftAccum /= 12.0;
            vec3 rayTint = mix(vec3(1.15, 0.56, 0.14), vec3(1.25, 1.10, 0.72), rayReach);
            color += shaftAccum * rayTint * (rayEnv * rayReach * 0.88 * bloomScale);
        }
    }

    // 5. Pre-Detonation Breath-Holding Vignette Tension (t = 17..28)
    if (normT >= 17.0 && dt < 0.0) {
        float holdP = smoothstep(17.0, 27.5, normT);
        float vig = smoothstep(0.38, 1.28, length(centeredScreen * 2.0));
        color *= (1.0 - 0.34 * holdP * vig * distFactor);
    }

    // 6. Anime Sakuga "Nakamura Impact Frame" Snap (dt = 0..4.5)
    if (impactFlag > 0.5 && flashStrength > 0.01 && dt >= 0.0 && dt < 4.5) {
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        vec2 toCore = (vUv - blastUv) * vec2(aspect, 1.0);
        float radCore = length(toCore);
        float ang = atan(toCore.y, toCore.x);
        // Crisp anime radial shock lines radiating from the detonation center
        float rays = pow(0.5 + 0.5 * sin(ang * 26.0 + floor(dt * 2.0) * 1.9), 4.0) * smoothstep(0.10, 0.85, radCore);

        if (dt < 2.0) {
            // Beat 1 (0..2 ticks): High-contrast posterized crimson-black vs blinding white-gold snap
            float snapEnv = sin(clamp(dt / 2.0, 0.0, 1.0) * 3.14159265) * flashStrength * distFactor;
            float thresh = smoothstep(0.34, 0.62, luma + exp(-radCore * 2.4) * 0.45 + rays * 0.28);
            vec3 inkShadow = vec3(0.04, 0.01, 0.015);
            vec3 blindingCore = vec3(1.22, 1.08, 0.82);
            vec3 impactFrameCol = mix(inkShadow, blindingCore, thresh);
            color = mix(color, impactFrameCol, clamp(snapEnv * 0.68, 0.0, 0.72));
        } else {
            // Beat 2 (2..4.5 ticks): Warm golden-white exposure release back into full color
            float relP = clamp((dt - 2.0) / 2.5, 0.0, 1.0);
            float relEnv = (1.0 - relP) * (1.0 - relP) * flashStrength * distFactor;
            float coreBloom = mix(0.32, 0.92, inFront * exp(-radCore * 1.6));
            color = mix(color, vec3(1.06, 0.94, 0.70), clamp(relEnv * coreBloom * 0.52, 0.0, 0.55));
        }
    }

    fragColor = vec4(clamp(color, vec3(0.0), vec3(1.0)), 1.0);
}
