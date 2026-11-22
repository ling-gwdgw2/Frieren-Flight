#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// Pass 1: Relativistic Volumetric Black Hole Raymarcher (First-Principles Clean-Room Shader)
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

// 3D lattice value noise interpolated from the 512x512 NoiseSampler.b white-noise channel
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

// Samples the smooth organic cloud channels (R, G) of NoiseSampler with warm filament tinting
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
void Haze(inout vec3 color, vec3 pos, float baseScale, float horizonThreshold, float alpha, float stepScale, float crit, float shadowAlive) {
    if (shadowAlive <= 0.001) return;
    float r = length(pos);
    float photonShellR = 0.72 * baseScale;
    float dShell = max(r - photonShellR, 0.0) / max(baseScale, 0.25);
    float innerMask = smoothstep(horizonThreshold * 1.05, photonShellR * 1.15, r);
    float outerFade = exp(-dShell * dShell * 3.6) * smoothstep(2.6 * baseScale, 0.95 * baseScale, r);
    float corona = innerMask * outerFade / (dShell * dShell + 0.06);
    vec3 col = mix(vec3(1.18, 0.86, 0.58), vec3(0.95, 1.15, 1.55), crit * crit * 0.45) * (1.0 + 0.95 * crit * crit);

    color += col * corona * ((0.28 / 200.0) * stepScale * (1.0 - alpha) * shadowAlive);
}

// GasDisc() anchored at the relativistic ISCO (baseScale) with outer extent driven by outerScale (diskSize)
void GasDisc(
    inout vec3 color, inout float alpha,
    inout vec3 frontColor, inout float frontAlpha, bool isFrontSide,
    vec3 prevPos, vec3 pos, float verticalStep,
    float baseScale, float outerScale, float timeSec, float stepScale,
    float crit, float blastProg, bool isBlasting, float diskSurvive, float aberrationMode
) {
    if (diskSurvive <= 0.001) return;

    // Steep-angle plane-crossing blend: prevents top-down rays from stepping over the thin disk slab
    // while keeping grazing edge-on angles completely free of step-boundary noise spikes
    vec3 evalPos = pos;
    float dy = pos.y - prevPos.y;
    if (prevPos.y * pos.y < 0.0 && abs(dy) > 0.025) {
        float tCross = clamp(-prevPos.y / dy, 0.0, 1.0);
        vec3 crossPos = mix(prevPos, pos, tCross);
        evalPos = mix(pos, crossPos, smoothstep(0.025, 0.085, abs(dy)));
    }

    // During instantaneous collapse transition (0..0.065), sweep surviving accretion plasma outward cleanly into the ejecta wave
    if (isBlasting) {
        float rXZ = max(length(evalPos.xz), 1.0e-4);
        float shredOut = pow(clamp(blastProg / 0.065, 0.0, 1.0), 0.72) * 1.15 * baseScale;
        evalPos.xz *= max(1.0 - shredOut / rXZ, 0.50);
    }

    float critPinch = isBlasting ? 0.78 : mix(1.0, 0.78, pow(crit, 1.6));
    float discInner = 0.55 * baseScale;
    float discWidth = 5.3 * outerScale * critPinch;

    vec3 origin = vec3(0.0, 0.0, 0.0);
    vec3 discNormal = vec3(0.0, 1.0, 0.0);
    float discThickness = 0.1 * mix(baseScale, outerScale, 0.35) * (1.0 + 0.18 * crit);

    float distFromCenter = distance(evalPos, origin);
    float distFromDisc = dot(discNormal, evalPos - origin);

    // Ensure radialGradient reaches 0.0 smoothly at the outer wing boundary well inside boundRadius
    float radialGradient = 1.0 - saturate((distFromCenter - discInner) / max(discWidth * 1.15, 0.5));
    float radCurve = pcurve(radialGradient, 4.0, 0.9);
    if (radCurve <= 1.0e-6) {
        return;
    }

    float coverage = radCurve;

    discThickness *= radialGradient;
    float effThickness = max(discThickness, verticalStep * 0.65 * radialGradient);
    coverage *= saturate(1.0 - abs(distFromDisc) / max(effThickness, 1.0e-5));

    // Radial thermal gradient across the accretion slab: fiery amber-orange outer dust -> white-gold core -> cool inner rim
    vec3 thermalTint = mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(pow(radialGradient, 2.0)));
    thermalTint *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(pow(radialGradient, 0.5)));
    if (crit > 0.01) {
        thermalTint = mix(thermalTint, vec3(1.25, 1.32, 1.65), crit * crit * pow(radialGradient, 1.4) * 0.45);
    }

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

    float speed = 0.06 * (1.0 + 1.65 * pow(crit, 1.5));

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

    if (crit > 0.001) {
        dustColor *= (1.0 + 1.15 * crit * crit);
    }

    coverage = saturate(coverage * (1200.0 / 200.0) * stepScale) * diskSurvive;
    dustColor = max(vec3(0.0), dustColor) * diskSurvive;

    coverage *= radCurve;

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

// Integrates smooth 3D domain-warped ionized plasma filaments across the hollow relativistic shock shell (stipple-free)
void integrateShockShellSegment(
    vec3 roLocal, vec3 rdLocal, float segStart, float segEnd,
    float rIn, float rOut, float shockR, float blastProg, float timeSec, float dither, int sampleCount,
    inout vec3 shellHdr, inout float shellAlpha
) {
    if (segEnd <= segStart) return;
    float dt = (segEnd - segStart) / float(sampleCount);
    float shellSpan = max(rOut - rIn, 0.15);
    float cool = smoothstep(0.06, 0.74, blastProg);
    float shellLife = smoothstep(0.0, 0.030, blastProg) * pow(1.0 - smoothstep(0.28, 0.86, blastProg), 1.25);
    // Volumetric plasma interior stays semi-transparent while analytic crest supplies a clean, stipple-free supersonic rim
    float intensity = (12.5 / shellSpan) * mix(1.55, 0.42, smoothstep(0.03, 0.68, blastProg)) * shellLife;

    vec3 hotEdge = mix(vec3(0.66, 1.06, 1.88), vec3(1.52, 1.20, 0.74), cool);
    vec3 midPlasma = mix(vec3(1.58, 1.10, 0.58), vec3(1.40, 0.54, 0.16), cool);
    vec3 wakeEmber = mix(vec3(1.22, 0.40, 0.12), vec3(0.84, 0.20, 0.05), cool);

    float spin = blastProg * 0.48;
    float cSpin = cos(spin);
    float sSpin = sin(spin);

    for (int s = 0; s < 16; s++) {
        if (s >= sampleCount) break;
        float t = segStart + (float(s) + dither) * dt;
        vec3 p = roLocal + rdLocal * t;
        float rDist = length(p);
        float shellRel = clamp((rDist - rIn) / shellSpan, 0.0, 1.0);

        // Smooth wide volumetric bell across wall thickness (analytic rim crest handles the sharp Mach skin without stipple)
        float body = pow(max(sin(shellRel * PI), 0.0), 1.25);

        vec3 dirU = p / max(rDist, 1.0e-4);
        // Strong Fresnel/grazing weight keeps the bubble interior clear over dark starry space
        float mu = abs(dot(dirU, rdLocal));
        float grazing = mix(0.030, 0.78, pow(clamp(1.0 - mu, 0.0, 1.0), 2.15));
        float baseWeight = body * grazing;
        if (baseWeight <= 0.0005) {
            continue;
        }

        vec3 rotDir = vec3(dirU.x * cSpin - dirU.z * sSpin, dirU.y, dirU.x * sSpin + dirU.z * cSpin);

        // Ridged multi-octave turbulence creates crisp cosmic shock striations & plasma tendrils
        float r1 = 1.0 - abs(noise(rotDir * 7.2 - vec3(0.0, blastProg * 2.0, timeSec * 0.12)));
        float r2 = 1.0 - abs(noise(rotDir * 15.5 + vec3(r1 * 1.4, -blastProg * 3.1, r1 * 0.9)));
        float r3 = 1.0 - abs(noise(rotDir * 32.0 - vec3(r2 * 0.9, blastProg * 4.4, r1 * 1.1)));
        float ridge = clamp(r1 * 0.44 + r2 * 0.34 + r3 * 0.22, 0.0, 1.0);
        float filaments = pow(smoothstep(0.36, 0.95, ridge), 1.75) * 1.50;

        float wallProfile = baseWeight * filaments;

        vec3 sampleCol = mix(wakeEmber, midPlasma, smoothstep(0.10, 0.62, shellRel));
        sampleCol = mix(sampleCol, hotEdge, smoothstep(0.58, 0.95, shellRel));

        float stepAlpha = clamp(wallProfile * (dt / shellSpan) * 0.11 * shellLife, 0.0, 0.25);
        shellHdr += (1.0 - shellAlpha * 0.65) * sampleCol * (wallProfile * dt * intensity);
        shellAlpha = (1.0 - shellAlpha) * stepAlpha + shellAlpha;
    }
}

// Evaluates the 3D Relativistic Singularity Collapse: compact remnant core + hollow limb-brightened plasma shell + analytic equatorial ejecta ring
void evaluateRelativisticDetonation(
    vec3 roLocal, vec3 rdLocal, float sceneDistUnits, float baseScale,
    float blastProg, float timeSec, float dither,
    inout vec3 color, inout float alpha
) {
    // Sedov-Taylor blast front framed cleanly from initial core breakaway out to full horizon sweep
    float sedov = pow(max(blastProg, 1.0e-4), 0.54);
    float shockR = 0.42 * baseScale + 12.2 * sedov;
    float shellHalfThick = mix(0.16, 0.64, sedov);
    float shellLife = smoothstep(0.0, 0.030, blastProg) * pow(1.0 - smoothstep(0.28, 0.86, blastProg), 1.25);

    vec3 blastColor = vec3(0.0);
    float blastAlpha = 0.0;

    // 1. Razor-Tight Singularity Pinch Core (blazing compact star flash leaving hollow clear vacuum inside the shock rim)
    float coreFade = 1.0 - smoothstep(0.05, 0.26, blastProg);
    if (coreFade > 0.002) {
        float tCore = clamp(-dot(roLocal, rdLocal), 0.0, sceneDistUnits);
        vec3 pClosest = roLocal + rdLocal * tCore;
        float bCore = length(pClosest);
        float snapPeak = exp(-pow((blastProg - 0.018) / 0.042, 2.0));
        float rCore = mix(0.09, 0.26, pow(clamp(blastProg * 3.5, 0.0, 1.0), 0.55)) * baseScale;
        float coreGlow = 1.0 / (pow(bCore / max(rCore, 0.03), 3.0) + 0.045);
        coreGlow *= smoothstep(3.4 * rCore, 0.25 * rCore, bCore);

        float coreWisp = noise(normalize(pClosest + 1.0e-4) * 5.2 - rdLocal * 0.45 - vec3(0.0, blastProg * 3.0, 0.0)) * 0.28 + 0.78;
        vec3 earlyWhiteBlue = mix(vec3(0.84, 1.14, 1.84), vec3(1.56, 1.38, 1.20), smoothstep(0.0, 0.055, blastProg));
        vec3 lateAmber = mix(vec3(1.56, 1.34, 1.02), vec3(1.42, 0.52, 0.16), smoothstep(0.06, 0.24, blastProg));
        vec3 coreCol = mix(earlyWhiteBlue, lateAmber, smoothstep(0.04, 0.10, blastProg));

        float coreAmp = (5.0 + 26.0 * snapPeak) * coreFade;
        blastColor += coreCol * (coreGlow * coreWisp * coreAmp);
        blastAlpha = max(blastAlpha, clamp((1.0 - exp(-coreGlow * coreFade * 0.035)) * 0.65, 0.0, 0.78));
    }

    // 2. Analytic Front & Back Wall Integration + Smooth Analytic Supersonic Rim Caustic
    float rOut = shockR + shellHalfThick;
    float rIn = max(0.04, shockR - shellHalfThick);
    float b = dot(roLocal, rdLocal);
    float cOut = dot(roLocal, roLocal) - rOut * rOut;
    float dOut = b * b - cOut;

    if (dOut > 0.0) {
        float sOut = sqrt(dOut);
        float tO0 = max(-b - sOut, 0.0);
        float tO1 = min(-b + sOut, sceneDistUnits);
        if (tO1 > tO0) {
            float cIn = dot(roLocal, roLocal) - rIn * rIn;
            float dIn = b * b - cIn;
            if (dIn <= 0.0) {
                integrateShockShellSegment(
                    roLocal, rdLocal, tO0, tO1, rIn, rOut, shockR, blastProg, timeSec, dither, 14,
                    blastColor, blastAlpha
                );
            } else {
                float sIn = sqrt(dIn);
                float tI0 = -b - sIn;
                float tI1 = -b + sIn;
                if (tI0 > tO0) {
                    integrateShockShellSegment(
                        roLocal, rdLocal, tO0, min(tO1, tI0), rIn, rOut, shockR, blastProg, timeSec, dither, 8,
                        blastColor, blastAlpha
                    );
                }
                if (tO1 > tI1) {
                    integrateShockShellSegment(
                        roLocal, rdLocal, max(tO0, tI1), tO1, rIn, rOut, shockR, blastProg, timeSec, dither, 7,
                        blastColor, blastAlpha
                    );
                }
            }
        }
    }

    // Stipple-free analytic ionized Mach shock crest along the sphere silhouette edge (evaluated only if unobstructed)
    float tCenter = -b;
    if (shellLife > 0.002 && tCenter > 0.0 && tCenter <= sceneDistUnits) {
        float impact3D = length(roLocal + rdLocal * tCenter);
        float rimWidth = mix(0.065, 0.22, sedov) * mix(baseScale, 1.0, 0.5);
        float rimDist = (impact3D - shockR) / max(rimWidth, 0.04);
        float rimK = rimDist > 0.0 ? 2.4 : 1.1;
        float rimProfile = exp(-rimDist * rimDist * rimK);
        if (rimProfile > 0.002) {
            float cool = smoothstep(0.05, 0.70, blastProg);
            vec3 rimTint = mix(vec3(0.68, 1.08, 1.92), vec3(1.62, 1.16, 0.62), cool);
            float rimAmp = mix(115.0, 26.0, smoothstep(0.05, 0.70, blastProg)) * shellLife;
            blastColor += rimTint * (rimProfile * rimAmp);
            blastAlpha = max(blastAlpha, clamp(rimProfile * 0.42 * shellLife, 0.0, 0.65));
        }
    }

    // 3. Analytic Shattered Equatorial Accretion Ejecta Ring (100% continuous spiral streaks, zero raymarch striping)
    float eqLife = smoothstep(0.008, 0.042, blastProg) * pow(1.0 - smoothstep(0.26, 0.78, blastProg), 1.35);
    if (eqLife > 0.002) {
        float eqFront = mix(0.60 * baseScale, shockR * 0.90, smoothstep(0.0, 0.14, blastProg));
        float eqWidth = mix(0.34, 1.18, sedov) * baseScale;
        float slabH = mix(0.10, 0.26, sedov) * mix(baseScale, 1.0, 0.5);

        float rdYabs = max(abs(rdLocal.y), 0.065);
        float rdYsign = (rdLocal.y >= 0.0) ? 1.0 : -1.0;
        float tPlane = -roLocal.y / (rdYsign * rdYabs);

        if (tPlane > 0.0 && tPlane <= sceneDistUnits) {
            vec3 pEq = roLocal + rdLocal * tPlane;
            float rPlanar = length(pEq.xz);
            float incWeight = clamp(abs(rdLocal.y) / 0.16, 0.35, 1.0);
            float optThick = min(slabH / rdYabs, 1.15) * incWeight;

            float rNorm = (rPlanar - eqFront) / max(eqWidth, 0.20);
            float ringK = rNorm > 0.0 ? 6.8 : 1.45;
            float ringProf = exp(-rNorm * rNorm * ringK);

            if (ringProf > 0.004) {
                float azimTurns = atan2(-pEq.x, -pEq.z) * (1.0 / TWO_PI);
                vec2 streakUv1 = vec2(azimTurns * 2.0 - log(max(rPlanar, 0.3)) * 0.25 - blastProg * 0.42, rPlanar * 0.11);
                vec2 streakUv2 = vec2(azimTurns * 3.0 - log(max(rPlanar, 0.3)) * 0.18 - blastProg * 0.58 + 0.37, rPlanar * 0.19);
                vec3 streak = pow(sampleOrganicStreak(streakUv1), vec3(2.05)) * 1.45
                            + pow(sampleOrganicStreak(streakUv2), vec3(2.35)) * 1.25;

                vec3 eqWarm = mix(vec3(1.68, 1.24, 0.72), vec3(1.42, 0.56, 0.18), smoothstep(0.08, 0.64, blastProg));
                vec3 eqCrest = mix(vec3(1.75, 1.48, 1.15), eqWarm, smoothstep(0.12, 0.55, blastProg));
                float leadingEdge = exp(-pow((rNorm - 0.15) / 0.35, 2.0));
                vec3 eqCol = mix(eqWarm * streak, eqCrest, leadingEdge * 0.55);

                blastColor += eqCol * (ringProf * optThick * 135.0 * eqLife);
                blastAlpha = max(blastAlpha, clamp(ringProf * optThick * 0.48 * eqLife, 0.0, 0.72));
            }
        }
    }

    color += blastColor;
    alpha = clamp(alpha + blastAlpha * (1.0 - alpha), 0.0, 1.0);
}

// Inverse-square gravitational ray deflection step scaled by arbitrary geodesic step size ds
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

    bool isBlasting = (HoleState.y > 1.002);
    float blastProg = isBlasting ? clamp((HoleState.y - 1.005) / 0.995, 0.0, 1.0) : 0.0;
    float crit = isBlasting ? 1.0 : clamp(HoleState.y, 0.0, 1.0);
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
    float sceneDistUnits = (!isActualSky) ? (sceneDistance / uScale) : 1.0e5;

    float sedov = isBlasting ? pow(max(blastProg, 1.0e-4), 0.54) : 0.0;
    float shockR = 0.42 * baseScale + 12.2 * sedov;
    float shellHalfThick = mix(0.16, 0.64, sedov);
    float activeBound = isBlasting ? max(boundRadius, shockR + shellHalfThick + 1.5) : boundRadius;

    vec3 camPosEye = -centre / uScale;
    float holeDistUnits = length(camPosEye);
    float bProj = dot(camPosEye, rayDir);
    float cSphere = dot(camPosEye, camPosEye) - boundRadius * boundRadius;
    float disc = bProj * bProj - cSphere;
    float root = (disc >= 0.0) ? sqrt(disc) : -1.0;
    float tFar = -bProj + root;

    if (!isBlasting) {
        if (disc < 0.0 || tFar <= 0.0) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        if (!isActualSky && along > 0.0 && sceneDistance < (along - boundRadius * uScale)) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    } else if (holeDistUnits > activeBound) {
        float cActive = dot(camPosEye, camPosEye) - activeBound * activeBound;
        float discActive = bProj * bProj - cActive;
        float rootActive = (discActive >= 0.0) ? sqrt(discActive) : -1.0;
        if (discActive < 0.0 || (-bProj + rootActive) <= 0.0) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        if (!isActualSky && along > 0.0 && sceneDistance < (along - activeBound * uScale)) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    }

    // Orthonormal basis mapping eye space into local black-hole disk coordinates (discNormal = +Y)
    vec3 axis = normalize(SpinAxis.xyz);
    vec3 camDirUnit = normalize(camPosEye + rayDir * 1.0e-5);
    // Clamp to a minimum 0.052-radian grazing elevation so edge-on camera angles still display disk thickness
    float sinElev = dot(camDirUnit, axis);
    if (abs(sinElev) < 0.052) {
        float elevSign = sinElev >= 0.0 ? 1.0 : -1.0;
        float targetSin = elevSign * 0.052;
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

    vec3 roLocal = vec3(dot(camPosEye, diskU), dot(camPosEye, axis), dot(camPosEye, diskV));
    vec3 eyevec = normalize(vec3(dot(rayDir, diskU), dot(rayDir, axis), dot(rayDir, diskV)));
    vec3 camDirLocal = normalize(vec3(dot(camDirUnit, diskU), dot(camDirUnit, axis), dot(camDirUnit, diskV)));

    float worldGlowFlag = clamp(DiskShape.x, 0.0, 1.0);
    float diskBrightness = clamp(DiskShape.w, 0.0, 2.0);
    float cinematicWeight = clamp(DiskU.w, 0.0, 1.0);
    float lensMult = (SpinAxis.w >= 0.05) ? 1.0 : 0.0;
    float aberrationMode = SpinAxis.w;
    float dither = r2Dither(gl_FragCoord.xy);

    float shadowAlive = isBlasting ? (1.0 - smoothstep(0.0, 0.030, blastProg)) : 1.0;
    float diskSurvive = isBlasting ? (1.0 - smoothstep(0.0, 0.065, blastProg)) : 1.0;

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    vec3 frontColor = vec3(0.0);
    float frontAlpha = 0.0;
    bool hitShadow = false;

    if (diskSurvive > 0.002 && disc >= 0.0 && tFar > 0.0 && (isActualSky || along <= 0.0 || sceneDistance >= (along - boundRadius * uScale))) {
        float halfSpan = boundRadius;
        float tStart = max(holeDistUnits - halfSpan, 0.0);
        vec3 raypos = roLocal + eyevec * tStart;

        int cfgSteps = clamp(int(DiskShape.z + 0.5), 32, 128);
        int steps = clamp(cfgSteps + 64, 96, MAX_RAY_STEPS);
        float marchSpan = (holeDistUnits + halfSpan) - tStart;
        float ds = marchSpan / float(steps);
        float stepScale = ds / 0.075;
        raypos += eyevec * dither * ds;

        float horizonThreshold = 0.52 * baseScale * shadowAlive;
        float blastLensScale = isBlasting ? (1.0 - smoothstep(0.0, 0.14, blastProg)) : 1.0;
        float effLensMult = lensMult * blastLensScale;
        float maxHazeR = 2.62 * baseScale;
        float discOuterGuardR = 0.55 * baseScale + 5.3 * outerScale * 1.15 + ds;
        float discBaseThickness = 0.15 * mix(baseScale, outerScale, 0.35);

        for (int i = 0; i < MAX_RAY_STEPS; i++) {
            if (i >= steps || alpha > 0.995) {
                break;
            }

            vec3 prevPos = raypos;
            WarpSpace(eyevec, raypos, stepScale, effLensMult);
            raypos += eyevec * ds;

            vec3 posEye = centre + (diskU * raypos.x + axis * raypos.y + diskV * raypos.z) * uScale;
            float distEye = length(posEye);
            if (!isActualSky && distEye >= sceneDistance) {
                break;
            }

            float rNow = length(raypos);
            if (shadowAlive > 0.001 && rNow < horizonThreshold) {
                hitShadow = true;
                break;
            }

            float verticalStep = abs(raypos.y - prevPos.y);
            float discGuardY = max(discBaseThickness, verticalStep * 0.65) + 0.32;
            bool inDiscSlab = (abs(raypos.y) <= discGuardY) || (abs(prevPos.y) <= discGuardY) || (prevPos.y * raypos.y <= 0.0);
            if (inDiscSlab && (isBlasting || rNow <= discOuterGuardR)) {
                bool isFrontSide = (dot(raypos, camDirLocal) > 0.02);
                GasDisc(
                    color, alpha, frontColor, frontAlpha, isFrontSide,
                    prevPos, raypos, verticalStep,
                    baseScale, outerScale, timeSec, stepScale,
                    crit, blastProg, isBlasting, diskSurvive, aberrationMode
                );
            }
            if (rNow < maxHazeR) {
                Haze(color, raypos, baseScale, horizonThreshold, alpha, stepScale, crit, shadowAlive);
            }
        }

        if (hitShadow) {
            // Preserve foreground accretion bar in front of the collapsing horizon without hard white circle cutouts
            color = frontColor;
            alpha = mix(frontAlpha, 1.0, shadowAlive);
        }
    }

    float swallowedBoost = 1.0 + swallowed * 0.035;
    float cinematicBoost = 1.0 + cinematicWeight * 0.14 + worldGlowFlag * 0.04;
    float critEnergy = isBlasting ? 1.0 : (1.0 + crit * 0.95);
    float stateEnergy = openFactor * critEnergy * swallowedBoost * diskBrightness * cinematicBoost;
    color *= stateEnergy;

    // Act 4 Relativistic Singularity Collapse: 3D volumetric Mach shock shell, equatorial plasma ejecta & core flash
    if (isBlasting) {
        vec3 rdUnbent = normalize(vec3(dot(rayDir, diskU), dot(rayDir, axis), dot(rayDir, diskV)));
        vec3 blastCol = vec3(0.0);
        float blastAlpha = 0.0;
        evaluateRelativisticDetonation(
            roLocal, rdUnbent, sceneDistUnits, baseScale,
            blastProg, timeSec, dither,
            blastCol, blastAlpha
        );
        color += blastCol * (openFactor * max(diskBrightness, 0.25) * cinematicBoost);
        alpha = clamp(alpha + blastAlpha * (1.0 - alpha), 0.0, 1.0);
    }

    vec3 encodedHdr = saturate(color * 0.0001) * 100.0;
    float transmittance = clamp(1.0 - alpha * openFactor, 0.0, 1.0);

    fragColor = vec4(encodedHdr, transmittance);
}
