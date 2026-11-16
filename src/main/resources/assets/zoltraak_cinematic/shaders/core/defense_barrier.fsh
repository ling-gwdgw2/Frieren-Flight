#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec3 worldPos;
in vec3 viewPosition;

out vec4 fragColor;

// Hash and noise helpers for barrier arcs and impact sparks
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.55 + noise(p * 2.15) * 0.30 + noise(p * 4.41) * 0.15;
}

void main() {
    // 1. Surface normal and view direction (guarded against zero-length vectors)
    vec3 vCross = cross(dFdx(viewPosition), dFdy(viewPosition));
    float vLen = length(vCross);
    vec3 viewNormal = vLen > 0.0001 ? vCross / vLen : vec3(0.0, 0.0, 1.0);

    float viewLen = length(viewPosition);
    vec3 viewDir = viewLen > 0.0001 ? -viewPosition / viewLen : vec3(0.0, 0.0, 1.0);

    float facing = clamp(abs(dot(viewNormal, viewDir)), 0.0, 1.0);

    // 2. Highlight reflection on hex plate facets
    vec3 keyLight = normalize(vec3(-0.35, 0.75, 0.55));
    vec3 half1 = normalize(keyLight + viewDir);
    float spec1 = pow(max(0.0, dot(viewNormal, half1)), 36.0);

    vec3 fillLight = normalize(vec3(0.55, -0.25, 0.75));
    vec3 half2 = normalize(fillLight + viewDir);
    float spec2 = pow(max(0.0, dot(viewNormal, half2)), 20.0);

    // 3. Edge glow with slight color fringe
    float edgeR = pow(1.0 - facing, 3.2);
    float edgeG = pow(1.0 - facing, 2.5);
    float edgeB = pow(1.0 - facing, 2.0);
    float rimGlow = pow(1.0 - facing, 4.8);

    // 4. Barrier pulse and crackling mana arcs
    float t = GameTime * 2.5;
    float localDist = length(worldPos.xz);
    float pulse1 = sin(worldPos.y * 1.5 - t * 2.0 + localDist * 1.2);
    float pulse2 = cos(localDist * 2.5 - t * 2.8);
    float shimmer = 0.5 + 0.5 * (pulse1 * 0.55 + pulse2 * 0.45);

    vec2 arcCoord = worldPos.xy * 2.8 + vec2(t * 0.6, -t * 0.4);
    float arcNoise = fbm(arcCoord);
    float manaArcs = pow(max(0.0, arcNoise - 0.58) * 2.38, 3.0) * 1.8;

    // 5. Hit ripple and impact sparks
    float hitFactor = smoothstep(0.12, 0.85, vertexColor.r);
    float hitDist = length(worldPos);
    float ripple1 = sin(hitDist * 8.0 - t * 14.0) * hitFactor;
    float ripple2 = cos(hitDist * 15.0 - t * 22.0) * hitFactor;
    float hitRipple = max(0.0, ripple1 * 0.65 + ripple2 * 0.35);
    float hitSparks = pow(noise(worldPos.xz * 5.0 + vec2(t * 4.0, -t * 3.0)), 3.5) * hitFactor * 3.5;

    // 6. Barrier color palette
    vec3 darkBlue = vec3(0.02, 0.07, 0.22);
    vec3 cyan = vec3(0.06, 0.82, 1.0);

    vec3 basePlate = mix(darkBlue, cyan, edgeB * 0.75);
    vec3 baseColor = mix(basePlate, vertexColor.rgb, 0.50);
    vec3 rimColor = vec3(0.05 + 0.90 * edgeR, 0.65 + 0.35 * edgeG, 1.0);

    vec3 color = baseColor * (0.80 + 0.25 * shimmer);
    color += rimColor * (edgeB * 0.85 + rimGlow * 0.70);
    color += vec3(0.85, 0.98, 1.0) * (spec1 * 0.85) + vec3(0.30, 0.75, 1.0) * (spec2 * 0.30);
    color += vec3(0.50, 0.90, 1.0) * manaArcs;
    color += vec3(0.85, 0.97, 1.0) * hitRipple;
    color += vec3(1.0, 1.0, 1.0) * hitSparks;

    // 7. Keep center clearer to see through while keeping edges crisp
    float alpha = vertexColor.a * (0.24 + 0.76 * edgeB);
    alpha = clamp(alpha + hitFactor * 0.35 + manaArcs * 0.25 + hitRipple * 0.30 + spec1 * 0.40, 0.0, 1.0);

    alpha *= ColorModulator.a;
    color *= ColorModulator.rgb;

    // GPU-safe single terminal discard at end of shader
    if (alpha <= 0.003) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
