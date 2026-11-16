#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;

in vec2 uv;
in vec3 viewPosition;
in float age;
in float energy;
in float span;
in float halfWidth;

out vec4 fragColor;

// Clean analytic noise
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
    bool isVolume = uv.y >= 2.0;
    float x = uv.x;
    if (x < 0.0) discard;

    vec3 whiteCore   = vec3(1.00, 1.00, 1.00);
    vec3 iceRim      = vec3(0.62, 0.80, 1.00);

    if (isVolume) {
        // 1. 3D cylindrical beam column: crisp pure-white body with subtle ice-periwinkle limb shading
        vec3 crossP = cross(dFdx(viewPosition), dFdy(viewPosition));
        float crossLen = length(crossP);
        vec3 normal = crossLen > 0.0001 ? crossP / crossLen : vec3(0.0, 0.0, 1.0);
        float viewLen = length(viewPosition);
        vec3 viewDir = viewLen > 0.0001 ? -viewPosition / viewLen : vec3(0.0, 0.0, 1.0);
        float facing = abs(dot(normal, viewDir));

        vec3 volColor = mix(iceRim, whiteCore, pow(clamp(facing, 0.0, 1.0), 0.40));
        float volAlpha = clamp(energy * ColorModulator.a, 0.0, 0.99);
        if (volAlpha < 0.003) discard;

        fragColor = vec4(volColor * ColorModulator.rgb, volAlpha);
        return;
    }

    // 2. Camera-facing outer aura quad: Frieren anime radial profile with fibrous bristles
    float y = abs(uv.y * 2.0 - 1.0);
    float beamSpan = max(span, 1.0);
    float distBase = x * beamSpan;
    float dxCap = max(0.0, (x - 1.0) * beamSpan / 22.5);
    float r = clamp(sqrt(dxCap * dxCap + y * y), 0.0, 1.0);

    // Sheared high-frequency anime energy bristles along the beam column
    float shear = distBase * 3.2 - y * 5.0 - age * 2.4;
    float fiberA = noise2D(vec2(shear, y * 18.0));
    float fiberB = noise2D(vec2(shear * 2.4 + 7.3, y * 38.0));
    float streakLong = noise2D(vec2(distBase * 0.75 - age * 1.4, y * 14.0));
    float wisp = fiberA * 0.50 + fiberB * 0.35 + streakLong * 0.15;
    float rCoreWisp = max(0.0, r - (wisp - 0.5) * 0.042 * smoothstep(0.24, 0.38, r));
    float rCoronaWisp = max(0.0, r - (wisp - 0.48) * 0.115 * smoothstep(0.30, 0.52, r));

    float core = 1.0 - smoothstep(0.33, 0.385, rCoreWisp);
    float rim = exp(-pow((rCoreWisp - 0.38) / 0.065, 2.0));
    float coronaEnv = exp(-pow(max(0.0, rCoronaWisp - 0.36) / 0.21, 1.75));
    float bristles = pow(clamp(wisp * 1.18, 0.0, 1.0), 1.85) * smoothstep(0.32, 0.40, r) * exp(-max(0.0, r - 0.38) * 4.6);
    float corona = clamp(coronaEnv * (0.72 + 0.45 * wisp) + bristles * 0.55, 0.0, 1.0);

    vec3 deepIndigo  = vec3(0.14, 0.20, 0.62);
    vec3 royalCobalt = vec3(0.26, 0.44, 0.98);
    vec3 iceRimBright = vec3(0.62, 0.86, 1.00);

    vec3 color = mix(deepIndigo, royalCobalt, clamp(corona * 1.15 + bristles * 0.60, 0.0, 1.0));
    color = mix(color, iceRimBright, clamp(rim * 0.95 + bristles * 0.45, 0.0, 1.0));
    color = mix(color, whiteCore, clamp(core, 0.0, 1.0));

    float edgeCut = 1.0 - smoothstep(0.68, 0.86, r);
    float rawAlpha = max(core * 0.98, max(rim * 0.88, corona * 0.82));
    float alpha = clamp(rawAlpha * energy * edgeCut * ColorModulator.a, 0.0, 1.0);
    if (alpha < 0.003) discard;

    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
