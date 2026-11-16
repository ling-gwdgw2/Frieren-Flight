#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D CosmicSampler;

in vec2 vUv;
out vec4 fragColor;

uniform mat4 InverseProjectionMat;
uniform mat4 InvViewRotMat;
// xyz = singularity center in eye space (-Z forward), w = gravitational scale r_g in blocks
uniform vec4 HoleCentre;
// xyz = normalized spin axis in eye space, w = lensing mode (0 = off, 1 = on, 2 = chromatic fringe)
uniform vec4 SpinAxis;
// x = open progress (0..1), y = criticality (0..1) + blast (0..1), z = elapsed seconds, w = absorbed count
uniform vec4 HoleState;
// x = terrain lighting flag (>0.5 on), y = disk radius in r_g, z = ray steps (32..128), w = emission scale
uniform vec4 DiskShape;
// xyz = equatorial reference vector in eye space, w = cutscene boost weight (0..1)
uniform vec4 DiskU;

const float SKY_DEPTH_THRESHOLD = 0.9999;
const int MAX_RAY_STEPS = 128;
const float TWO_PI = 6.283185307;
const float PI = 3.141592654;
const float PLANCK_C2_UM_K = 14387.77;
const float HORIZON_RADIUS = 0.29;
const float PHOTON_RING_RADIUS = 0.445;
const float DISK_INNER_RADIUS = 0.50;

// Roberts R2 low-discrepancy screen-space phase in [0, 1) to eliminate ray step banding
float r2ScreenDither(vec2 fragPx) {
    return fract(dot(fragPx, vec2(0.7548776662, 0.5698402910)));
}

// Spectral blackbody RGB from Planck's law at representative RGB wavelengths (0.635, 0.538, 0.452 um),
// white-balanced at 6200 K solar photosphere temperature
vec3 blackbodyPlanckSpectrum(float kelvin) {
    float temp = max(kelvin, 950.0);
    const vec3 lambdaUm = vec3(0.635, 0.538, 0.452);
    const vec3 lambdaNeg5 = vec3(
        1.0 / (0.635 * 0.635 * 0.635 * 0.635 * 0.635),
        1.0 / (0.538 * 0.538 * 0.538 * 0.538 * 0.538),
        1.0 / (0.452 * 0.452 * 0.452 * 0.452 * 0.452)
    );
    const vec3 solarRefRaw = vec3(0.25727, 0.30106, 0.31417);

    vec3 exponent = min(vec3(PLANCK_C2_UM_K) / (lambdaUm * temp), vec3(76.0));
    vec3 spectral = lambdaNeg5 / max(exp(exponent) - 1.0, vec3(1.0e-7));
    vec3 balanced = spectral / solarRefRaw;
    float peak = max(max(balanced.r, balanced.g), max(balanced.b, 1.0e-5));
    return clamp(balanced / peak, 0.0, 1.0);
}

// Fast rational approximation of Gauss error function erf(x) for analytic disk slab integration
float approxErf(float x) {
    float ax = abs(x);
    float t = 1.0 / (1.0 + 0.3275911 * ax);
    float poly = (((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t);
    float mag = 1.0 - poly * exp(-ax * ax);
    return x >= 0.0 ? mag : -mag;
}

// Fraction of segment [h0, h1] weighted by a vertical Gaussian plasma density exp(-(h/scaleH)^2)
float gaussianSlabFraction(float h0, float h1, float scaleH) {
    float u0 = h0 / scaleH;
    float u1 = h1 / scaleH;
    float du = abs(u1 - u0);
    if (du < 0.025) {
        float uMid = 0.5 * (u0 + u1);
        return exp(-uMid * uMid);
    }
    return clamp((0.886226925 / du) * abs(approxErf(u1) - approxErf(u0)), 0.0, 1.0);
}

// Integer 3D permutation hash (GLSL 150 compatible) returning uniform [0, 1]^3 per voxel
vec3 pcgCellHash3(ivec3 cell) {
    uvec3 v = uvec3(cell) ^ uvec3(0x9E3779B9u);
    v.x = v.x * 1664525u + 1013904223u;
    v.y = v.y * 22695477u + 1u;
    v.z = v.z * 1103515245u + 12345u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return vec3(v) * (1.0 / 4294967295.0);
}

// Procedural star layer inside celestial coordinates
vec3 sampleStarField(vec3 skyDir, float gridRes, float densityCap, float timeSec, float bandMask) {
    vec3 scaled = skyDir * gridRes;
    ivec3 cellIdx = ivec3(floor(scaled));
    vec3 local = fract(scaled) - 0.5;
    vec3 rng = pcgCellHash3(cellIdx);

    float cutoff = mix(densityCap, densityCap * 0.55, bandMask);
    if (rng.x > cutoff) {
        return vec3(0.0);
    }

    vec3 jitter = (rng - 0.5) * 0.70;
    float d = length(local - jitter);
    float radius = mix(0.044, 0.084, rng.y);
    float profile = exp(-(d * d) / max(radius * radius, 0.0002));
    if (profile < 0.002) {
        return vec3(0.0);
    }

    float kelvin = mix(3000.0, 15500.0, pow(max(rng.z, 1.0e-5), 1.3));
    vec3 starTint = blackbodyPlanckSpectrum(kelvin);
    float shimmer = 0.84 + 0.16 * sin(timeSec * (2.1 + rng.y * 3.4) + rng.x * TWO_PI * 10.0);
    float flux = pow(max(1.0 - rng.x / max(cutoff, 0.001), 1.0e-5), 1.65) * (1.30 + bandMask * 0.85);
    return starTint * (profile * flux * shimmer);
}

// Samples the rotated Milky Way backdrop, star layers, and lensing magnification
vec3 sampleGalacticSky(vec3 worldDir, float timeSec, float bendWeight, float baseMipLod) {
    vec3 dir = normalize(worldDir);

    float drift = timeSec * 0.0035;
    float cd = cos(drift);
    float sd = sin(drift);
    vec3 spun = vec3(cd * dir.x - sd * dir.z, dir.y, sd * dir.x + cd * dir.z);

    const mat3 galacticTilt = mat3(
         0.8290,  0.4755, -0.2939,
        -0.5592,  0.7053, -0.4359,
         0.0000,  0.5257,  0.8507
    );
    vec3 gal = normalize(galacticTilt * spun);

    float phi = atan(gal.z, gal.x);
    float theta = asin(clamp(gal.y, -0.9999, 0.9999));
    vec2 equirectUv = vec2(phi / TWO_PI + 0.5, theta / PI + 0.5);

    float lod = clamp(baseMipLod - bendWeight * 0.5, 0.0, 4.0);
    vec3 galaxyTex = textureLod(CosmicSampler, equirectUv, lod).rgb;

    if (bendWeight > 0.05 && SpinAxis.w > 1.5) {
        vec2 fringe = vec2(0.0018 * bendWeight, 0.0009 * bendWeight);
        galaxyTex.r = textureLod(CosmicSampler, vec2(fract(equirectUv.x + fringe.x), clamp(equirectUv.y + fringe.y, 0.001, 0.999)), lod).r;
        galaxyTex.b = textureLod(CosmicSampler, vec2(fract(equirectUv.x - fringe.x), clamp(equirectUv.y - fringe.y, 0.001, 0.999)), lod).b;
    }

    vec3 lin = pow(max(galaxyTex, vec3(1.0e-5)), vec3(1.92));
    float luma = dot(lin, vec3(0.2126, 0.7152, 0.0722));
    float bandWeight = exp(-gal.y * gal.y * 8.5);
    float coreAlign = max(0.0, dot(gal, normalize(vec3(0.85, 0.0, 0.52))));
    float coreAlign2 = coreAlign * coreAlign;
    float coreHalo = coreAlign2 * coreAlign2 * bandWeight;

    vec3 dustGrade = mix(vec3(0.78, 0.88, 1.18), vec3(1.18, 0.96, 0.76), clamp(luma * 1.6 + coreHalo * 0.6, 0.0, 1.0));
    vec3 skyColor = lin * dustGrade * (2.15 + coreHalo * 0.85);
    float brightCloud = smoothstep(0.18, 0.75, luma);
    skyColor += lin * brightCloud * vec3(1.22, 0.95, 0.80) * 1.15;

    skyColor += sampleStarField(gal, 138.0, 0.095, timeSec, bandWeight);
    skyColor += sampleStarField(gal + vec3(0.37, 0.71, 0.19), 296.0, 0.14, timeSec * 0.82, bandWeight) * 0.65;

    float lensAmplify = 1.0 + bendWeight * 1.32 + pow(max(bendWeight, 1.0e-5), 2.4) * 0.92;
    skyColor *= lensAmplify;
    return skyColor / (1.0 + skyColor * 0.28);
}

// Screen-space terrain illumination cast by the black hole & accretion disk
vec3 computeWorldTerrainGlow(
    vec3 rayDir, float sceneDistance, bool isActualSky, vec3 centre, vec3 axis,
    float rg, float openFactor, float crit, float blast, float diskBrightness
) {
    vec3 pos = rayDir * min(sceneDistance, 300.0);
    vec3 grad = cross(dFdx(pos), dFdy(pos));
    float gradLen = length(grad);
    if (isActualSky || sceneDistance >= 240.0 || gradLen <= 1.0e-6) {
        return vec3(0.0);
    }

    vec3 surfN = grad / gradLen;
    if (dot(surfN, rayDir) > 0.0) {
        surfN = -surfN;
    }

    vec3 toHole = centre - pos;
    float dist = length(toHole);
    vec3 lightDir = toHole / max(dist, 0.01);

    float lambert = clamp(dot(surfN, lightDir) * 0.65 + 0.35, 0.0, 1.0);
    vec3 halfVec = normalize(lightDir - rayDir);
    float spec = pow(max(1.0e-5, dot(surfN, halfVec)), 24.0) * 0.45;

    float eqAlign = 1.0 - abs(dot(lightDir, axis));
    vec3 diskAmber = vec3(1.18, 0.86, 0.52);
    vec3 ringBlue = vec3(0.76, 0.90, 1.30);
    vec3 tint = mix(ringBlue, diskAmber, smoothstep(0.25, 0.85, eqAlign));
    if (blast > 0.001) {
        tint = mix(tint, vec3(1.6, 1.4, 1.1), blast);
    }

    float rNorm = dist / max(rg * 18.0, 4.0);
    float atten = (1.0 / (1.0 + rNorm * rNorm * 0.85)) * smoothstep(235.0, 45.0, sceneDistance);
    float power = openFactor * (1.0 + crit * 0.8 + blast * 2.5) * diskBrightness;
    return tint * ((lambert * 0.55 + spec) * atten * power);
}

// Schwarzschild null geodesic photon acceleration: a = -1.5 * r_s * |p x d|^2 / r^5 * p
vec3 geodesicAcceleration(vec3 pos, vec3 dirVec, float lensMult) {
    float r = length(pos);
    float rSafe = max(r, HORIZON_RADIUS * 0.92);
    vec3 angMom = cross(pos, dirVec);
    float h2 = dot(angMom, angMom);
    float invR5 = 1.0 / (rSafe * rSafe * rSafe * rSafe * rSafe);
    return pos * (-1.5 * HORIZON_RADIUS * h2 * invR5 * lensMult);
}

// Accumulates volumetric accretion emission & Beer-Lambert absorption across curved segment [p0, p1]
void integrateAccretionSegment(
    vec3 p0, vec3 p1, vec3 rayStepDir, vec3 axis, vec3 diskU, vec3 diskV,
    float stepLen, float discOuter, float outerSpan, float timeSec, float crit, float blast,
    inout vec3 accumRadiance, inout float transmittance
) {
    float h0 = dot(p0, axis);
    float h1 = dot(p1, axis);
    float dh = h1 - h0;
    float tPlane = abs(dh) > 1.0e-5 ? clamp(-h0 / dh, 0.0, 1.0) : 0.5;
    vec3 pDisk = mix(p0, p1, tPlane);

    float hSample = dot(pDisk, axis);
    vec3 planar = pDisk - axis * hSample;
    float r = length(planar);
    float minH = (h0 * h1 <= 0.0) ? 0.0 : min(abs(h0), abs(h1));

    float u = clamp((r - DISK_INNER_RADIUS) / max(discOuter - DISK_INNER_RADIUS, 0.95), 0.0, 1.0);
    float scaleH = 0.044 * (1.0 - 0.30 * u) + 0.072 * smoothstep(0.05, 0.55, u) * (1.0 - u);

    if (r >= 0.42 && minH < scaleH * 3.5) {
        float slabCov = gaussianSlabFraction(h0, h1, max(scaleH, 0.01));
        float iscoTaper = smoothstep(0.45, 0.55, r);
        float outerMask = pow(max(smoothstep(1.0, 0.08, u), 1.0e-5), 1.35) * clamp((discOuter - r) / outerSpan, 0.0, 1.0);

        float invRatio = DISK_INNER_RADIUS / max(r, DISK_INNER_RADIUS);
        float torqueEmiss = pow(invRatio, 1.85) * max(1.0 - 0.88 * sqrt(invRatio), 0.05) * 4.8 * iscoTaper * outerMask;

        if (torqueEmiss > 0.0004) {
            float flowAngle = atan(dot(planar, diskV), dot(planar, diskU));
            float az01 = flowAngle / TWO_PI + 0.5;
            float orbitPhase = timeSec * 0.105;

            vec2 uvCoarse = vec2(r * 0.68 - orbitPhase * 0.11, fract(az01 + r * 0.24 - orbitPhase));
            vec2 uvFine = vec2(r * 1.72 + orbitPhase * 0.09, fract(az01 * 2.0 + r * 0.46 - orbitPhase * 1.38));
            float nCoarse = textureLod(NoiseSampler, fract(uvCoarse), 0.0).r;
            float nFine = textureLod(NoiseSampler, fract(uvFine), 0.0).r;
            float shearStreaks = 0.78 + 0.22 * sin(r * 14.5 - flowAngle * 2.0 + nCoarse * 2.4);
            float fbm = (0.36 + 0.64 * (0.64 * nCoarse + 0.36 * nFine)) * shearStreaks;

            float density = torqueEmiss * (fbm * fbm);
            float tau = density * (stepLen * slabCov) * 16.5;

            if (tau > 0.0004) {
                float baseKelvin = 1750.0 + 7900.0 * pow(invRatio, 1.25) + fbm * 1450.0 + crit * 2400.0 + blast * 4200.0;

                // Physical 3D orbital Doppler beaming + gravitational redshift from camera viewing direction
                vec3 tangRaw = cross(axis, planar);
                float tangLen = length(tangRaw);
                vec3 spinTangent = tangLen > 1.0e-5 ? (tangRaw / tangLen) : diskU;
                float beta = clamp(sqrt(0.14 / max(r, 0.38)), 0.08, 0.50);
                float vTowardObs = dot(spinTangent, -rayStepDir) * beta;
                float lorentzInv = sqrt(max(1.0 - beta * beta, 0.12));
                float gravRedshift = sqrt(max(1.0 - HORIZON_RADIUS / max(r, HORIZON_RADIUS * 1.12), 0.25));
                float doppler = (lorentzInv / max(1.0 - vTowardObs, 0.25)) * gravRedshift;

                float obsKelvin = baseKelvin * mix(1.0, doppler, 0.48);
                vec3 thermalRgb = blackbodyPlanckSpectrum(obsKelvin);
                float beamingGain = clamp(pow(doppler, 2.25), 0.38, 1.85);
                float radianceAmp = torqueEmiss * 3.15 * outerMask * beamingGain * (0.65 + 0.55 * fbm);

                float stepAlpha = 1.0 - exp(-tau);
                accumRadiance += transmittance * stepAlpha * thermalRgb * radianceAmp;

                float coreOpacity = mix(0.28, 0.92, smoothstep(2.1, 0.56, r));
                transmittance *= exp(-tau * coreOpacity);
            }
        }
    }

    // Continuous photon-sphere coronal glow wrapping near r_ph = 1.5 * r_s
    vec3 pMid = 0.5 * (p0 + p1);
    float dPhoton = abs(length(pMid) - PHOTON_RING_RADIUS);
    if (dPhoton < 0.24) {
        float dpNorm = dPhoton / 0.058;
        float halo = exp(-dpNorm * dpNorm) * 0.15 * stepLen;
        accumRadiance += transmittance * halo * blackbodyPlanckSpectrum(9400.0);
    }
}

void main() {
    float rg = max(HoleCentre.w, 0.0001);
    vec3 sceneColour = texture(SceneSampler, vUv).rgb;

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
    float impact = length(centre - rayDir * along);

    float openFactor = clamp(HoleState.x, 0.0, 1.0);
    float crit = clamp(HoleState.y, 0.0, 1.0);
    float blast = clamp(HoleState.y - 1.0, 0.0, 1.0);
    float timeSec = HoleState.z;

    if (openFactor < 0.0001) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    vec3 worldRay = normalize((InvViewRotMat * vec4(rayDir, 0.0)).xyz);

    float sceneDistance = 1.0e9;
    float depth = texture(DepthSampler, vUv).r;
    if (depth > 0.0001 && depth < SKY_DEPTH_THRESHOLD) {
        vec4 sceneClip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 sceneEye = InverseProjectionMat * sceneClip;
        if (abs(sceneEye.w) > 1.0e-6 && (sceneEye.z / sceneEye.w) < 0.0) {
            sceneDistance = length(sceneEye.xyz / sceneEye.w);
        }
    }

    float uScale = max(rg * 2.2, 0.05);
    vec3 camPos = -centre / uScale;
    vec3 d = rayDir;

    bool isActualSky = (depth >= SKY_DEPTH_THRESHOLD) || (sceneDistance >= 350.0);
    float isSky = isActualSky ? smoothstep(-0.02, 0.14, worldRay.y) : 0.0;

    vec3 axis = normalize(SpinAxis.xyz);
    float diskBrightness = max(DiskShape.w, 0.0);

    vec3 terrainGlow = vec3(0.0);
    if (DiskShape.x > 0.5) {
        terrainGlow = computeWorldTerrainGlow(
            rayDir, sceneDistance, isActualSky, centre, axis, rg, openFactor, crit, blast, diskBrightness
        );
        sceneColour += sceneColour * terrainGlow * 1.35 + terrainGlow * 0.22;
    }

    float discOuter = clamp(DiskShape.y * (4.00 / 22.0), 1.45, 7.25);
    float outerSpan = max(0.60, discOuter * 0.425);
    float boundRadius = max(5.5, discOuter + 1.5);
    float maxHoleDist = holeDistance + boundRadius * uScale;
    bool blockedByTerrain = !isActualSky && (sceneDistance < maxHoleDist) && (sceneDistance < 350.0);

    float bProj = dot(camPos, rayDir);
    float cSphere = dot(camPos, camPos) - boundRadius * boundRadius;
    float disc = bProj * bProj - cSphere;
    float root = (disc >= 0.0) ? sqrt(disc) : -1.0;
    float tFar = -bProj + root;

    if (disc < 0.0 || tFar < 0.0) {
        if (isSky > 0.001) {
            vec3 spaceSky = sampleGalacticSky(worldRay, timeSec, 0.0, baseMipLod);
            fragColor = vec4(mix(sceneColour, spaceSky, isSky * openFactor), 1.0);
            return;
        }
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    float tNear = max(-bProj - root, 0.0);
    vec3 p = camPos + d * tNear;
    float travelledBlocks = tNear * uScale;
    if (blockedByTerrain && travelledBlocks >= sceneDistance) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    vec3 diskU;
    if (dot(DiskU.xyz, DiskU.xyz) > 0.25) {
        diskU = normalize(DiskU.xyz);
    } else {
        vec3 refVec = abs(axis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        diskU = normalize(cross(axis, refVec));
    }
    vec3 diskV = cross(axis, diskU);

    float totalDist = tFar - tNear;
    int steps = clamp(int(DiskShape.z + 0.5), 24, MAX_RAY_STEPS);
    float stepLength = totalDist / float(steps);
    float subDs = stepLength * 0.5;
    float lensMult = (SpinAxis.w >= 0.05) ? 1.0 : 0.0;

    // Slight phase offset via R2 screen dither eliminates residual azimuthal step seams
    float phase = (r2ScreenDither(gl_FragCoord.xy) - 0.5) * stepLength;
    p += d * max(phase, -tNear * 0.5);
    travelledBlocks += max(phase, -tNear * 0.5) * uScale;

    vec3 accumRadiance = vec3(0.0);
    float transmittance = 1.0;
    float minDist = 100.0;
    bool hitShadow = false;
    bool hitTerrain = false;

    for (int i = 0; i < MAX_RAY_STEPS; ++i) {
        if (i >= steps || transmittance < 0.002) {
            break;
        }

        vec3 pSegStart = p;

        // Two RK2 midpoint geodesic substeps keep shadow and photon ring invariant across 32..128 step presets
        for (int sub = 0; sub < 2; ++sub) {
            float rPre = length(p);
            minDist = min(minDist, rPre);
            if (rPre < HORIZON_RADIUS) {
                hitShadow = true;
                transmittance = 0.0;
                break;
            }

            vec3 a1 = geodesicAcceleration(p, d, lensMult);
            vec3 dMid = normalize(d + a1 * (0.5 * subDs));
            vec3 pMid = p + d * (0.5 * subDs);
            vec3 a2 = geodesicAcceleration(pMid, dMid, lensMult);

            d = normalize(d + a2 * subDs);
            p += dMid * subDs;
            travelledBlocks += subDs * uScale;

            if (blockedByTerrain && travelledBlocks >= sceneDistance) {
                hitTerrain = true;
                break;
            }

            float rPost = length(p);
            minDist = min(minDist, rPost);
            if (rPost < HORIZON_RADIUS) {
                hitShadow = true;
                transmittance = 0.0;
                break;
            }
        }

        if (hitShadow || hitTerrain) {
            break;
        }

        integrateAccretionSegment(
            pSegStart, p, d, axis, diskU, diskV,
            stepLength, discOuter, outerSpan, timeSec, crit, blast,
            accumRadiance, transmittance
        );
    }

    // Crisp photonic Einstein rim outside the shadow silhouette
    if (!hitShadow && !hitTerrain) {
        float ringDeltaNorm = (minDist - 0.465) / 0.022;
        float photonRing = exp(-ringDeltaNorm * ringDeltaNorm);
        accumRadiance += blackbodyPlanckSpectrum(10800.0) * (photonRing * 1.95 * transmittance);
    }

    // Smooth Hermite deflection blend toward bounding-sphere boundary to prevent horizon step seams
    float discNorm = clamp(disc / (boundRadius * boundRadius), 0.0, 1.0);
    float edgeFade = smoothstep(0.0, 0.25, discNorm);
    vec3 lensedD = normalize(mix(rayDir, d, edgeFade));

    vec3 lensedBg;
    if (hitTerrain) {
        lensedBg = sceneColour;
    } else {
        vec2 lensOffset = (lensedD.xy - rayDir.xy) * (0.025 * openFactor);
        vec3 bg = texture(SceneSampler, clamp(vUv + lensOffset, 0.001, 0.999)).rgb;
        bg += bg * terrainGlow * 1.35 + terrainGlow * 0.22;

        float bendAmount = length(lensedD - rayDir);
        float bendWeight = smoothstep(0.005, 0.28, bendAmount);

        vec3 worldD = (InvViewRotMat * vec4(lensedD, 0.0)).xyz;
        vec3 lensedSky = sampleGalacticSky(worldD, timeSec, bendWeight, baseMipLod);

        float lensSkyReveal = isActualSky
            ? smoothstep(0.06, 0.26, bendAmount) * 0.90
            : smoothstep(0.14, 0.38, bendAmount) * 0.75;
        float skyMask = clamp(isSky + lensSkyReveal, 0.0, 1.0);
        lensedBg = mix(bg, lensedSky, skyMask);
    }

    vec3 behindEffect = lensedBg * transmittance;
    if (hitShadow) {
        if (crit >= 0.99 && blast > 0.001) {
            vec3 coreBurst = mix(vec3(1.4, 1.0, 0.6), vec3(5.0, 4.5, 3.8), blast);
            behindEffect = coreBurst * blast;
        } else {
            behindEffect = vec3(0.0);
        }
    }

    float swallowedBoost = 1.0 + clamp(HoleState.w, 0.0, 10.0) * 0.035;
    float cinematicBoost = 1.0 + clamp(DiskU.w, 0.0, 1.0) * 0.15;
    float blastDiskEnergize = (1.0 + blast * 3.5) * swallowedBoost;
    vec3 diskRaw = accumRadiance * (openFactor * (1.0 + crit * 1.5) * blastDiskEnergize * diskBrightness);

    vec3 haloGlow = vec3(0.0);
    if (along > 0.0 && !hitTerrain && (sceneDistance > holeDistance * 0.85 || !blockedByTerrain)) {
        vec3 impactVec = (rayDir * along - centre) / uScale;
        float impactR = length(impactVec);
        float impactH = dot(impactVec, axis);
        float impactPlanar = length(impactVec - axis * impactH);

        float ringDelta = abs(impactR - 0.68);
        float airyRim = 1.0 / (1.0 + ringDelta * ringDelta * 16.0);
        float airyWide = exp(-impactR * impactR * 0.45);
        vec3 shadowVeil = vec3(0.68, 0.75, 0.90) * (airyRim * 0.22 + airyWide * 0.10);

        float eqGlow = exp(-impactH * impactH * 22.0) * exp(-impactPlanar * impactPlanar * 0.38);
        vec3 eqVeil = vec3(1.10, 0.92, 0.72) * (eqGlow * 0.28);

        haloGlow = (shadowVeil + eqVeil) * (openFactor * (1.0 + crit * 0.8) * diskBrightness * cinematicBoost);
    }

    vec3 diskCombined = diskRaw + haloGlow;
    vec3 diskEmission = diskCombined / (diskCombined + 1.0);

    vec3 blastFx = vec3(0.0);
    if (blast > 0.001 && along > 0.0 && (!hitShadow || sceneDistance > holeDistance || !blockedByTerrain)) {
        float progress = 1.0 - blast;

        float coreRadius = rg * (1.2 + progress * 6.5);
        float coreNorm = impact / max(coreRadius, 0.1);
        float coreShape = exp(-coreNorm * coreNorm * 3.5);
        vec3 coreCol = mix(vec3(1.0, 0.65, 0.25), vec3(1.0, 0.98, 1.0), blast);
        blastFx += coreCol * (coreShape * blast * 7.5);

        float shockRadius = rg * (2.2 + progress * 24.0);
        float shockWidth = rg * (0.45 + progress * 1.1);
        float shockDist = abs(impact - shockRadius) / max(shockWidth, 0.1);
        float shockShape = exp(-shockDist * shockDist * 6.0);
        vec3 shockLead = vec3(0.4, 0.9, 1.0);
        vec3 shockBody = vec3(1.0, 0.88, 0.6);
        vec3 shockTrail = vec3(0.85, 0.45, 1.0);
        vec3 shockCol = mix(shockTrail, shockBody, clamp(blast * 1.4, 0.0, 1.0));
        if (impact > shockRadius) {
            shockCol = mix(shockBody, shockLead, clamp((impact - shockRadius) / max(shockWidth * 0.8, 0.05), 0.0, 1.0));
        }
        blastFx += shockCol * (shockShape * blast * 6.0);

        float subShockRadius = shockRadius * 0.62;
        float subDist = abs(impact - subShockRadius) / max(shockWidth * 0.75, 0.1);
        float subShape = exp(-subDist * subDist * 5.0);
        blastFx += vec3(0.95, 0.6, 0.2) * (subShape * blast * 2.8);

        vec3 rayVec = (rayDir * along - centre) / max(rg, 0.01);
        float rayAngle = atan(dot(rayVec, diskU), dot(rayVec, diskV));
        float rayHarmonics = sin(rayAngle * 14.0 + timeSec * 1.2) * cos(rayAngle * 9.0 - timeSec * 0.8);
        float raySpikes = pow(max(abs(rayHarmonics), 1.0e-5), 2.5);
        float rayFalloff = exp(-impact / max(rg * 16.0, 1.0));
        vec3 rayColor = mix(vec3(1.0, 0.85, 0.5), vec3(1.0, 0.98, 1.0), blast);
        blastFx += rayColor * (raySpikes * rayFalloff * blast * 5.0);

        float viewProximity = clamp(1.0 - impact / max(rg * 25.0, 1.0), 0.0, 1.0);
        blastFx += vec3(1.0, 0.95, 0.88) * (blast * blast * viewProximity * 2.8);
    }

    vec3 composite = behindEffect + diskEmission + blastFx;
    fragColor = vec4(mix(sceneColour, composite, openFactor), 1.0);
}
