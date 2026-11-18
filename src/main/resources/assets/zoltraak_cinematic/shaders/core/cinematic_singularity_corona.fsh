#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

// Swirling dark violet corona shader for Qual

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vertexColor;
in float age;

out vec4 fragColor;

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
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;

    float theta = atan(p.y, p.x);

    // 1. Inward swirling spiral (driven by continuous GameTime and cyclic vertex age phase)
    float agePhase = sin(age * 0.02463994) * 0.25;
    float time = (GameTime > 0.00001) ? (GameTime * 1440.0 + agePhase) : (age * 0.06);
    float spiral = theta * 6.0 + log(max(r, 0.001)) * 4.5 - time * 2.5;
    float vortexWave = sin(spiral) * 0.5 + 0.5;

    // 2. Dark inner center mask
    float coreMask = smoothstep(0.18, 0.32, r);

    // 3. Glowing inner ring
    float ring = exp(-pow((r - 0.40) * 10.0, 2.0));
    float innerRim = exp(-pow((r - 0.34) * 22.0, 2.0));

    // 4. Outward energy rays
    float streamers = pow(abs(cos(theta * 7.0 - time * 0.8 + sin(r * 12.0) * 0.6)), 8.0) * exp(-r * 2.8);

    // 5. Fine ring flicker
    float microArcs = noise2D(vec2(theta * 10.0, r * 16.0 - time * 3.0)) * ring;

    // 6. Violet color palette (Color.r carries encoded age; preserve full red channel in tint)
    vec3 deepViolet  = vec3(0.55, 0.10, 0.95);
    vec3 magentaGlow = vec3(0.85, 0.22, 0.88);
    vec3 lavenderHot = vec3(0.96, 0.92, 1.00);

    vec3 baseColor = mix(deepViolet, magentaGlow, vortexWave);
    baseColor *= vec3(1.0, vertexColor.g, vertexColor.b) * 1.15;
    vec3 color = mix(baseColor, lavenderHot, clamp(innerRim * 1.25 + ring * 0.35, 0.0, 1.0));

    // 7. Final alpha
    float intensity = (ring * 1.2 + innerRim * 1.5 + streamers * 0.85 + microArcs * 0.4) * coreMask;
    float edgeFade = 1.0 - smoothstep(0.80, 0.99, r);
    float alpha = intensity * edgeFade * vertexColor.a * ColorModulator.a;

    if (alpha < 0.003) discard;

    fragColor = vec4(color * ColorModulator.rgb, clamp(alpha, 0.0, 1.0));
}
