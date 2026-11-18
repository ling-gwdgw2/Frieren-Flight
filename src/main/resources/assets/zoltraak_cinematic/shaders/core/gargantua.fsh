#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// Pass 1: Relativistic Volumetric Black Hole Raymarcher (Modeled directly after Gargantua buffer/bufferA.glsl)
// Outputs RGBA16F: RGB = HDR Black Hole Radiance (scaled by 0.01), A = Background Transmittance (1.0 - alpha)

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;

in vec2 vUv;
out vec4 fragColor;

uniform mat4 InverseProjectionMat;
uniform vec4 HoleCentre;
uniform vec4 SpinAxis;
uniform vec4 HoleState;
uniform vec4 DiskShape;
uniform vec4 DiskU;

const float SKY_DEPTH_THRESHOLD = 0.9999;
const int MAX_RAY_STEPS = 192;
const float PI = 3.14159265;
const float TWO_PI = 6.283185307;
const vec3 MainColor = vec3(1.0);

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

vec3 saturate(vec3 x) {
    return clamp(x, vec3(0.0), vec3(1.0));
}

// Low-discrepancy R2 spatial dither (eliminates white-noise clumping without requiring temporal AA)
float r2Dither(vec2 fragCoord) {
    return fract(dot(fragCoord, vec2(0.7548776662, 0.5698402910)));
}

float pcurve(float x, float a, float b) {
    if (x <= 0.0 || x >= 1.0) return 0.0;
    float k = pow(a + b, a + b) / (pow(a, a) * pow(b, b));
    return k * pow(x, a) * pow(1.0 - x, b);
}

float atan2(float y, float x) {
    if (x > 0.0) {
        return atan(y / x);
    } else if (x == 0.0) {
        if (y > 0.0) return PI / 2.0;
        if (y < 0.0) return -(PI / 2.0);
        return 0.0;
    } else {
        if (y >= 0.0) return atan(y / x) + PI;
        return atan(y / x) - PI;
    }
}

// 3D hardware-filtered Value Noise by iq (adapted for 512x512 NoiseSampler.b white-noise channel)
float noise(in vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv0 = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
    vec2 uv1 = uv0 + vec2(37.0, 17.0);
    float r0 = textureLod(NoiseSampler, (uv0 + 0.5) / 512.0, 0.0).b;
    float r1 = textureLod(NoiseSampler, (uv1 + 0.5) / 512.0, 0.0).b;
    return -1.0 + 2.0 * mix(r0, r1, f.z);
}

// Samples the smooth organic cloud channels (R, G) of NoiseSampler with warm organic tint matching iChannel1 in bufferA.glsl
vec3 sampleOrganicStreak(vec2 uv) {
    vec2 rg = textureLod(NoiseSampler, uv, 0.0).rg;
    float normR = clamp((rg.r - 0.255) * (1.0 / (0.675 - 0.255)), 0.0, 1.0);
    float normG = clamp(rg.g * (1.0 / 0.694), 0.0, 1.0);
    float streak = mix(normR, normG, 0.22);
    return mix(0.14, 1.06, streak) * vec3(1.08, 0.78, 0.52);
}

void evaluateDiscOctaves(vec3 radialCoords, float timeSec, float speed, out float noise1, out float noise2) {
    noise1 = 1.0;
    vec3 rc = radialCoords;                     rc.y += timeSec * speed;
    noise1 *= noise(rc * 3.0) * 0.5 + 0.5;      rc.y -= timeSec * speed;
    noise1 *= noise(rc * 6.0) * 0.5 + 0.5;      rc.y += timeSec * speed;
    noise1 *= noise(rc * 12.0) * 0.5 + 0.5;     rc.y -= timeSec * speed;
    noise1 *= noise(rc * 24.0) * 0.5 + 0.5;     rc.y += timeSec * speed;

    noise2 = 2.0;
    rc = radialCoords + 30.0;
    noise2 *= noise(rc * 3.0) * 0.5 + 0.5;      rc.y += timeSec * speed;
    noise2 *= noise(rc * 6.0) * 0.5 + 0.5;      rc.y -= timeSec * speed;
    noise2 *= noise(rc * 12.0) * 0.5 + 0.5;     rc.y += timeSec * speed;
    noise2 *= noise(rc * 24.0) * 0.5 + 0.5;     rc.y -= timeSec * speed;
    noise2 *= mix(0.50, noise(rc * 48.0) * 0.5 + 0.5, 0.72);     rc.y += timeSec * speed;
    noise2 *= mix(0.55, noise(rc * 92.0) * 0.5 + 0.5, 0.42);     rc.y -= timeSec * speed;
}

// Monotonically decaying photon-sphere corona anchored to the relativistic shadow (zero detached outer torus ring at any diskSize)
void Haze(inout vec3 color, vec3 pos, float baseScale, float horizonThreshold, float alpha, float stepScale, float crit) {
    float r = length(pos);
    float photonShellR = 0.72 * baseScale;
    float dShell = max(r - photonShellR, 0.0) / max(baseScale, 0.25);
    float innerMask = smoothstep(horizonThreshold * 1.05, photonShellR * 1.15, r);
    float outerFade = exp(-dShell * dShell * 3.6) * smoothstep(2.6 * baseScale, 0.95 * baseScale, r);
    float corona = innerMask * outerFade / (dShell * dShell + 0.06);
    vec3 col = vec3(1.18, 0.86, 0.58) * (1.0 + 0.35 * crit);

    color += col * corona * (0.28 / 200.0) * stepScale * (1.0 - alpha);
}

// GasDisc() anchored at the relativistic ISCO (baseScale) with outer extent driven by outerScale (diskSize)
void GasDisc(
    inout vec3 color, inout float alpha,
    inout vec3 frontColor, inout float frontAlpha, bool isFrontSide,
    vec3 prevPos, vec3 pos, float verticalStep,
    float baseScale, float outerScale, float timeSec, float stepScale, float crit, float blast, float aberrationMode
) {
    // Steep-angle plane-crossing blend: prevents top-down rays from stepping over the thin disk slab
    // while keeping grazing edge-on angles completely free of step-boundary noise spikes
    vec3 evalPos = pos;
    float dy = pos.y - prevPos.y;
    if (prevPos.y * pos.y < 0.0 && abs(dy) > 0.025) {
        float tCross = clamp(-prevPos.y / dy, 0.0, 1.0);
        vec3 crossPos = mix(prevPos, pos, tCross);
        evalPos = mix(pos, crossPos, smoothstep(0.025, 0.085, abs(dy)));
    }

    float discInner = 0.55 * baseScale;
    float discWidth = 5.3 * outerScale;

    vec3 origin = vec3(0.0, 0.0, 0.0);
    vec3 discNormal = vec3(0.0, 1.0, 0.0);
    float discThickness = 0.1 * mix(baseScale, outerScale, 0.35) * (1.0 + 0.22 * crit);

    float distFromCenter = distance(evalPos, origin);
    float distFromDisc = dot(discNormal, evalPos - origin);

    // Ensure radialGradient reaches 0.0 smoothly at the outer wing boundary well inside boundRadius
    float radialGradient = 1.0 - saturate((distFromCenter - discInner) / max(discWidth * 1.15, 0.5));

    float coverage = pcurve(radialGradient, 4.0, 0.9);

    discThickness *= radialGradient;
    float effThickness = max(discThickness, verticalStep * 0.65 * radialGradient);
    coverage *= saturate(1.0 - abs(distFromDisc) / max(effThickness, 1.0e-5));

    // Thermal gradient from bufferA.glsl lines 140-141: fiery amber-orange outer dust -> white-gold core -> subtle cool inner edge
    vec3 thermalTint = mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(pow(radialGradient, 2.0)));
    thermalTint *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(pow(radialGradient, 0.5)));

    vec3 dustColorLit = MainColor * mix(vec3(1.0), thermalTint, 0.65);

    float dustGlow = 1.0 / (pow(1.0 - radialGradient, 2.0) * 290.0 + 0.002);
    vec3 dustColor = dustColorLit * dustGlow * 8.2;

    coverage = saturate(coverage * 0.7);

    float fade = pow((abs(distFromCenter - discInner) + 0.4), 4.0) * 0.04;
    float bloomFactor = 1.0 / (pow(distFromDisc, 2.0) * 40.0 + fade + 0.00002);
    vec3 b = MainColor * pow(bloomFactor, 1.5) * thermalTint;

    dustColor = mix(dustColor, b * 150.0, saturate(1.0 - coverage * 1.0));
    coverage = saturate(coverage + bloomFactor * bloomFactor * 0.1);

    if (coverage < 0.01) {
        return;
    }

    float rawAngle = atan2(-evalPos.x, -evalPos.z);
    vec3 radialCoords;
    radialCoords.x = distFromCenter * 1.5 + 0.55;
    radialCoords.y = rawAngle * 1.5;
    radialCoords.z = distFromDisc * 1.5;
    radialCoords *= 0.95;

    float speed = 0.06 * (1.0 + 0.45 * crit);

    float noise1;
    float noise2;
    evaluateDiscOctaves(radialCoords, timeSec, speed, noise1, noise2);

    // Seamless crossfade across the +Z branch cut (evalPos.z > 0, evalPos.x ~ 0) where atan2 jumps between -PI and +PI
    float rPlanar = max(length(evalPos.xz), 1.0e-4);
    float seamMask = smoothstep(0.65, 0.96, evalPos.z / rPlanar);
    if (seamMask > 0.001) {
        float altAngle = atan2(evalPos.x, evalPos.z) + PI;
        vec3 altCoords = radialCoords;
        altCoords.y = altAngle * 1.5 * 0.95;
        float altNoise1;
        float altNoise2;
        evaluateDiscOctaves(altCoords, timeSec, speed, altNoise1, altNoise2);
        noise1 = mix(noise1, altNoise1, seamMask);
        noise2 = mix(noise2, altNoise2, seamMask);
    }

    // Lift low noiseFloor slightly in outer dust so outer spiral arms glow amber-orange instead of dark pockets
    dustColor *= noise1 * 0.996 + 0.004;
    coverage *= noise2;

    // Seamless integer-period (2.0x per 2*PI) azimuthal UV for organic streak texture under GL_REPEAT
    float azimTurns = rawAngle * (1.0 / TWO_PI);
    vec2 streakUv = vec2(azimTurns * 2.0 + timeSec * speed * 0.075, radialCoords.x * 0.27);
    dustColor *= pow(sampleOrganicStreak(streakUv), vec3(1.85)) * 4.0;

    if (aberrationMode > 1.5) {
        float sideSign = clamp(-evalPos.x / max(length(evalPos.xz), 0.2), -1.0, 1.0);
        dustColor *= vec3(1.0 - sideSign * 0.06, 1.0, 1.0 + sideSign * 0.08);
    }

    if (crit > 0.001 || blast > 0.001) {
        dustColor *= (1.0 + crit * 1.25 + blast * 2.5);
    }

    coverage = saturate(coverage * (1200.0 / 200.0) * stepScale);
    dustColor = max(vec3(0.0), dustColor);

    coverage *= pcurve(radialGradient, 4.0, 0.9);

    // Couple background sky opacity strictly to actual glowing plasma brightness + core density so faint outer dust never turns black over bright skies
    float stepLuma = dot(dustColor, vec3(0.299, 0.587, 0.114));
    float emissionOpacityMask = smoothstep(2.0, 30.0, stepLuma);
    float alphaCoverage = coverage * emissionOpacityMask * mix(0.02, 0.90, pow(radialGradient, 2.0));

    vec3 stepEmission = (1.0 - alpha) * dustColor * coverage;
    color += stepEmission;
    alpha = (1.0 - alpha) * alphaCoverage + alpha;

    if (isFrontSide) {
        frontColor += (1.0 - frontAlpha) * dustColor * coverage;
        frontAlpha = (1.0 - frontAlpha) * alphaCoverage + frontAlpha;
    }
}

// Exact WarpSpace() from Gargantua buffer/bufferA.glsl generalized for arbitrary step size ds
void WarpSpace(inout vec3 eyevec, inout vec3 raypos, float stepScale, float lensMult) {
    vec3 origin = vec3(0.0, 0.0, 0.0);
    float singularityDist = distance(raypos, origin);
    float warpFactor = 1.0 / (pow(singularityDist, 2.0) + 0.000001);
    vec3 singularityVector = normalize(origin - raypos);
    float warpAmount = 5.0 * lensMult;

    eyevec = normalize(eyevec + singularityVector * warpFactor * (warpAmount / 200.0) * stepScale);
}

void main() {
    float rg = max(HoleCentre.w, 0.0001);
    float openFactor = clamp(HoleState.x, 0.0, 1.0);
    if (openFactor <= 0.0005) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec4 clip = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 1.0e-6) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 rayDir = normalize(eye.xyz / eye.w);
    vec3 centre = HoleCentre.xyz;
    float holeDistance = length(centre);
    float along = dot(centre, rayDir);
    float impact = length(centre - rayDir * along);

    float crit = clamp(HoleState.y, 0.0, 1.0);
    float blast = clamp(HoleState.y - 1.0, 0.0, 1.0);
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

    float massScale = 1.0 + min(swallowed * 0.014, 0.22);
    float baseScale = mix(0.35, 1.0, openFactor) * massScale;
    float outerScale = clamp(DiskShape.y / 22.0, 0.45, 1.80) * baseScale;
    float uScale = max(rg * 1.65, 0.05);
    float boundRadius = max(7.5, 6.2 * outerScale + 1.5);

    vec3 camPosEye = -centre / uScale;
    float bProj = dot(camPosEye, rayDir);
    float cSphere = dot(camPosEye, camPosEye) - boundRadius * boundRadius;
    float disc = bProj * bProj - cSphere;
    float root = (disc >= 0.0) ? sqrt(disc) : -1.0;
    float tFar = -bProj + root;

    if (disc < 0.0 || tFar <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    if (!isActualSky && along > 0.0 && sceneDistance < (along - boundRadius * uScale)) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Orthonormal basis mapping eye space into bufferA.glsl's local space (where discNormal = +Y)
    vec3 axis = normalize(SpinAxis.xyz);
    vec3 camDirUnit = normalize(camPosEye);
    // Preserve bufferA.glsl's minimum 0.052-radian grazing elevation so exact 0-degree edge-on views still show full disk structure
    float sinElev = dot(camDirUnit, axis);
    if (abs(sinElev) < 0.052) {
        float targetSin = (sinElev >= 0.0 ? 1.0 : -1.0) * 0.052;
        axis = normalize(axis + camDirUnit * (targetSin - sinElev));
    }

    vec3 diskU;
    if (dot(DiskU.xyz, DiskU.xyz) > 0.25) {
        diskU = normalize(DiskU.xyz - axis * dot(DiskU.xyz, axis));
    } else {
        vec3 refVec = abs(axis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        diskU = normalize(cross(axis, refVec));
    }
    vec3 diskV = normalize(cross(axis, diskU));

    float halfSpan = boundRadius;
    float holeDistUnits = length(camPosEye);
    float tStart = max(holeDistUnits - halfSpan, 0.0);
    vec3 startEye = camPosEye + rayDir * tStart;

    // Transform raypos, eyevec, and camera direction into local black-hole coordinates
    vec3 raypos = vec3(dot(startEye, diskU), dot(startEye, axis), dot(startEye, diskV));
    vec3 eyevec = normalize(vec3(dot(rayDir, diskU), dot(rayDir, axis), dot(rayDir, diskV)));
    vec3 camDirLocal = normalize(vec3(dot(camDirUnit, diskU), dot(camDirUnit, axis), dot(camDirUnit, diskV)));

    float worldGlowFlag = clamp(DiskShape.x, 0.0, 1.0);
    float diskBrightness = clamp(DiskShape.w, 0.0, 2.0);
    float cinematicWeight = clamp(DiskU.w, 0.0, 1.0);
    float lensMult = (SpinAxis.w >= 0.05) ? 1.0 : 0.0;
    float aberrationMode = SpinAxis.w;

    int cfgSteps = clamp(int(DiskShape.z + 0.5), 32, 128);
    int steps = clamp(cfgSteps + 64, 96, MAX_RAY_STEPS);
    float marchSpan = (holeDistUnits + halfSpan) - tStart;
    float ds = marchSpan / float(steps);
    float stepScale = ds / 0.075;

    float dither = r2Dither(gl_FragCoord.xy);
    raypos += eyevec * dither * ds;

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    vec3 frontColor = vec3(0.0);
    float frontAlpha = 0.0;
    bool hitShadow = false;
    float horizonThreshold = 0.52 * baseScale;

    for (int i = 0; i < MAX_RAY_STEPS; i++) {
        if (i >= steps || alpha > 0.995) {
            break;
        }

        vec3 prevPos = raypos;
        WarpSpace(eyevec, raypos, stepScale, lensMult);
        raypos += eyevec * ds;

        vec3 posEye = centre + (diskU * raypos.x + axis * raypos.y + diskV * raypos.z) * uScale;
        float distEye = length(posEye);
        if (!isActualSky && distEye >= sceneDistance) {
            break;
        }

        if (length(raypos) < horizonThreshold) {
            hitShadow = true;
            break;
        }

        bool isFrontSide = (dot(raypos, camDirLocal) > 0.02);
        float verticalStep = abs(raypos.y - prevPos.y);
        GasDisc(
            color, alpha, frontColor, frontAlpha, isFrontSide,
            prevPos, raypos, verticalStep,
            baseScale, outerScale, timeSec, stepScale, crit, blast, aberrationMode
        );
        Haze(color, raypos, baseScale, horizonThreshold, alpha, stepScale, crit);
    }

    if (hitShadow) {
        // Rays captured by the event horizon preserve continuous foreground accretion bar emission without behind-hole scatter
        color = frontColor;
        alpha = 1.0;
        if (crit >= 0.99 && blast > 0.001) {
            vec3 coreBurst = mix(vec3(1.7, 0.95, 0.45), vec3(3.5, 3.2, 2.8), blast);
            color += coreBurst * (blast * 650.0);
        }
    }

    float swallowedBoost = 1.0 + swallowed * 0.035;
    float cinematicBoost = 1.0 + cinematicWeight * 0.14 + worldGlowFlag * 0.04;
    float stateEnergy = openFactor * (1.0 + crit * 1.45) * (1.0 + blast * 3.2) * swallowedBoost * diskBrightness * cinematicBoost;
    color *= stateEnergy;

    // Act 4 Supernova shockwave & radial flare spikes in HDR space
    if (blast > 0.001 && along > 0.0 && (!hitShadow || sceneDistance > holeDistance || isActualSky)) {
        float progress = 1.0 - blast;
        vec3 blastHdr = vec3(0.0);

        float coreRadius = rg * (1.2 + progress * 6.5);
        float coreNorm = impact / max(coreRadius, 0.1);
        float coreShape = exp(-coreNorm * coreNorm * 3.5);
        vec3 coreCol = mix(vec3(1.7, 0.75, 0.25), vec3(1.3, 1.25, 1.35), blast);
        blastHdr += coreCol * (coreShape * blast * 420.0);

        float shockRadius = rg * (2.2 + progress * 24.0);
        float shockWidth = rg * (0.45 + progress * 1.1);
        float shockDist = abs(impact - shockRadius) / max(shockWidth, 0.1);
        float shockShape = exp(-shockDist * shockDist * 6.0);
        vec3 shockLead = vec3(0.55, 0.85, 1.35);
        vec3 shockBody = vec3(1.65, 1.15, 0.65);
        vec3 shockTrail = vec3(1.45, 0.55, 0.25);
        vec3 shockCol = mix(shockTrail, shockBody, clamp(blast * 1.4, 0.0, 1.0));
        if (impact > shockRadius) {
            shockCol = mix(shockBody, shockLead, clamp((impact - shockRadius) / max(shockWidth * 0.8, 0.05), 0.0, 1.0));
        }
        blastHdr += shockCol * (shockShape * blast * 340.0);

        vec3 rayVec = (rayDir * along - centre) / max(rg, 0.01);
        float rayAngle = atan2(dot(rayVec, diskV), dot(rayVec, diskU));
        float rayHarmonics = sin(rayAngle * 14.0 + timeSec * 1.2) * cos(rayAngle * 9.0 - timeSec * 0.8);
        float raySpikes = pow(max(abs(rayHarmonics), 1.0e-5), 2.5);
        float rayFalloff = exp(-impact / max(rg * 16.0, 1.0));
        vec3 rayColor = mix(vec3(1.6, 1.05, 0.55), vec3(1.25, 1.22, 1.30), blast);
        blastHdr += rayColor * (raySpikes * rayFalloff * blast * 290.0);

        color += blastHdr * max(diskBrightness, 0.25);
        alpha = max(alpha, clamp((coreShape + shockShape) * blast * 0.85, 0.0, 1.0));
    }

    vec3 encodedHdr = saturate(color * 0.0001) * 100.0;
    float transmittance = clamp(1.0 - alpha * openFactor, 0.0, 1.0);

    fragColor = vec4(encodedHdr, transmittance);
}
