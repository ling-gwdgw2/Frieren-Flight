#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vertexColor;
in vec3 worldPos;
in vec3 viewPosition;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    return noise(p) * 0.54 + noise(p * 2.05) * 0.28 + noise(p * 4.15) * 0.18;
}

void main() {
    float x = abs(uv.x * 2.0 - 1.0);
    float t = GameTime * 2.5;

    // 1. Two-layer rising flame noise
    vec2 p1 = vec2(uv.x * 3.2, uv.y * 1.8 - t * 4.8);
    float fire1 = fbm(p1);

    float sway = sin(uv.y * 7.0 + t * 3.5) * 0.22;
    vec2 p2 = vec2((uv.x + sway) * 5.0, uv.y * 3.2 - t * 7.2);
    float fire2 = fbm(p2);

    float flameNoise = fire1 * 0.58 + fire2 * 0.42;

    // 2. Fire texture mask
    vec4 texSample = texture(Sampler0, vec2(uv.x, uv.y - t * 0.8));
    float texLuma = texSample.r * texSample.a;
    float flameDensity = flameNoise * 0.65 + texLuma * 0.35;

    // 3. Bright white center core
    float core = exp(-pow(x * 3.0, 2.0));

    // 4. Jagged flame edges
    float edgeLick = (flameNoise - 0.5) * 0.22;
    float edgeMask = 1.0 - smoothstep(0.72 + edgeLick, 0.99, x);

    // 5. Fire color gradient: crimson -> orange -> gold -> white core
    vec3 deepEmber = vec3(0.92, 0.15, 0.03);
    vec3 solarAmber = vec3(1.0, 0.58, 0.12);
    vec3 brightGold = vec3(1.0, 0.88, 0.32);
    vec3 whiteHot = vec3(1.0, 0.99, 0.95);

    vec3 color = mix(deepEmber, solarAmber, smoothstep(0.12, 0.50, flameDensity));
    color = mix(color, brightGold, smoothstep(0.50, 0.82, flameDensity));
    color = mix(color, whiteHot, core * 0.95);

    // Flickering pulse
    float pulse = 0.90 + 0.10 * sin(uv.y * 12.0 - t * 8.0);
    color *= pulse * (1.0 + core * 0.45);

    // 6. Final alpha
    float alpha = (core * 0.80 + flameDensity * 0.65) * edgeMask * vertexColor.a;
    alpha = clamp(alpha * ColorModulator.a, 0.0, 1.0);
    color *= vertexColor.rgb * ColorModulator.rgb;

    if (alpha <= 0.003) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
