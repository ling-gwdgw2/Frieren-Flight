#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;

in vec2 uv;
in float age;
in float energy;
in float span;
in float halfWidth;

out vec4 fragColor;

// Hash and smooth noise for beam edges
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    float x = uv.x;
    float y = abs(uv.y * 2.0 - 1.0);

    // Muzzle fade-in and tapered spearhead tip
    float distBlocks = x * span;
    float baseFade = smoothstep(0.0, 0.035, x);
    float muzzleMask = smoothstep(0.35, 2.6, distBlocks);
    float tipFade = smoothstep(1.0, 0.85, x);

    // Swirling void flow
    float flow = smoothNoise(vec2(x * 36.0 - age * 1.8, y * 8.0 + age * 0.35));
    float sootSwirl = smoothNoise(vec2(x * 95.0 - age * 3.5, y * 22.0));

    // Beam thickness profile
    float beamRadius = (0.32 + 0.04 * (flow - 0.5)) * max(0.2, tipFade);
    float coreDist = y / max(beamRadius, 0.008);

    // Solid black inner core
    float voidCore = (1.0 - smoothstep(0.72, 0.98, coreDist)) * muzzleMask;

    // Glowing violet rim and sparks
    float filament = pow(max(0.0, sin(x * 65.0 - age * 9.0 + y * 28.0 + flow * 4.0)), 10.0) * exp(-y * 4.0);
    float edgeGlow = exp(-pow((coreDist - 0.96) * 6.5, 2.0)) * 0.85;
    float sparks = smoothstep(0.60, 0.92, sootSwirl) * edgeGlow;

    // Color gradient: black core to violet edge
    vec3 voidBlack   = vec3(0.006, 0.004, 0.012);
    vec3 darkPurple  = vec3(0.11, 0.04, 0.19);
    vec3 violet      = vec3(0.62, 0.22, 0.92);
    vec3 lightViolet = vec3(0.90, 0.80, 1.00);

    vec3 color = mix(darkPurple, violet, smoothstep(0.08, 0.72, edgeGlow));
    color = mix(color, lightViolet, clamp(sparks * 1.2 + filament * 0.85, 0.0, 1.0));
    color = mix(color, voidBlack, voidCore * 0.92);

    // Final alpha
    float edgeCut = 1.0 - smoothstep(0.88, 1.0, y);
    float alpha = max(voidCore * 0.90, (edgeGlow + filament + sparks) * 0.92) * energy * baseFade * tipFade * edgeCut * ColorModulator.a;
    if (alpha < 0.004) discard;

    fragColor = vec4(color * ColorModulator.rgb, clamp(alpha, 0.0, 1.0));
}
