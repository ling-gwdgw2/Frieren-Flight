#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;

in vec2 uv;
in float age;
in float energy;
in float span;
in float halfWidth;

out vec4 fragColor;

// Clean analytic noise for anime longitudinal energy streaks & edge wisps
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2D(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
        mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

void main() {
    float x = uv.x;
    float y = abs(uv.y * 2.0 - 1.0);

    // World-space distances in blocks
    float distBase = x * span;
    float distTip  = (1.0 - x) * span;
    float distY    = y * halfWidth;

    // 1. Rounded elliptical base cap at magic circle origin & clearance zone
    float rLinear = clamp(distY / max(halfWidth, 0.01), 0.0, 1.0);
    float baseCapLen = 0.34 * halfWidth;
    float dxBase = max(0.0, baseCapLen - distBase) / max(baseCapLen, 0.001);
    float r = clamp(sqrt(rLinear * rLinear + dxBase * dxBase * 0.13), 0.0, 1.0);
    float baseFade = smoothstep(0.0, 0.04, distBase + (1.0 - rLinear) * 0.05);
    float circleClearance = smoothstep(0.20, 1.25, distBase);

    // 2. Rounded beam tip cap
    float capRadius = min(halfWidth * 0.85, max(0.35, span * 0.45));
    float dxDome = max(0.0, capRadius - distTip);
    float dyDome = distY;
    float domeDistance = sqrt(dxDome * dxDome + dyDome * dyDome);
    float headCap = 1.0 - smoothstep(capRadius * 0.72, capRadius, domeDistance);

    // 3. Sheared high-frequency anime energy fibers (angled brushstroke bristles)
    float shear = distBase * 11.0 - rLinear * halfWidth * 4.2 - age * 24.0;
    float fiberA = noise2D(vec2(shear, rLinear * 22.0));
    float fiberB = noise2D(vec2(shear * 2.4 + 7.3, rLinear * 46.0));
    float streakLong = noise2D(vec2(distBase * 2.6 - age * 18.0, rLinear * 16.0));
    float wisp = fiberA * 0.50 + fiberB * 0.35 + streakLong * 0.15;
    float rCoreWisp = max(0.0, r - (wisp - 0.5) * 0.042 * smoothstep(0.24, 0.38, r));
    float rCoronaWisp = max(0.0, r - (wisp - 0.48) * 0.115 * smoothstep(0.30, 0.52, r));

    // 4. Frieren anime radial profile (dominant white core + fibrous royal-cobalt/indigo sheath)
    float core = 1.0 - smoothstep(0.33, 0.385, rCoreWisp);
    float rim = exp(-pow((rCoreWisp - 0.38) / 0.065, 2.0));

    float coronaEnv = exp(-pow(max(0.0, rCoronaWisp - 0.36) / 0.21, 1.75));
    float bristles = pow(clamp(wisp * 1.18, 0.0, 1.0), 1.85) * smoothstep(0.32, 0.40, r) * exp(-max(0.0, r - 0.38) * 4.6);
    float corona = clamp(coronaEnv * (0.72 + 0.45 * wisp) + bristles * 0.55, 0.0, 1.0);

    // 5. Anime color composition (outside-in: ultramarine indigo -> royal cobalt -> ice rim -> white core)
    vec3 deepIndigo  = vec3(0.14, 0.20, 0.62);
    vec3 royalCobalt = vec3(0.26, 0.44, 0.98);
    vec3 iceRim      = vec3(0.62, 0.86, 1.00);
    vec3 whiteCore   = vec3(1.00, 1.00, 1.00);

    // Near the magic circle, keep any outer glow clean sky-ice blue so it never darkens the runes
    vec3 outerBase = mix(iceRim, deepIndigo, circleClearance);
    vec3 outerMid  = mix(iceRim, royalCobalt, circleClearance);

    vec3 color = mix(outerBase, outerMid, clamp(corona * 1.15 + bristles * 0.60, 0.0, 1.0));
    color = mix(color, iceRim, clamp(rim * 0.95 + bristles * 0.45, 0.0, 1.0));
    color = mix(color, whiteCore, clamp(core, 0.0, 1.0));

    // 6. Soft outer feathering strictly bounded to corona (no detached dark sky trough)
    float outerCoronaAlpha = corona * 0.84 * mix(0.25, 1.0, circleClearance);
    float edgeCut = 1.0 - smoothstep(0.68, 0.86, r);
    float rawAlpha = max(core * 0.99, max(rim * 0.90, outerCoronaAlpha));
    float alpha = clamp(rawAlpha * energy * baseFade * headCap * edgeCut * ColorModulator.a, 0.0, 1.0);

    if (alpha < 0.003) discard;

    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
