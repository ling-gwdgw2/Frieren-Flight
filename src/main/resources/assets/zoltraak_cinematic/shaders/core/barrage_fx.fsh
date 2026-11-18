#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

void main() {
    // Map disc UV [0, 1] into centered [-1, 1] coordinates (aligned with Sampler0)
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float t = GameTime * 2.5;

    // 1. Sharp outer shockwave ring
    float shockRadius = 0.74;
    float ringDist = abs(r - shockRadius);
    float shockPeak = exp(-pow(ringDist * 28.0, 2.0)) * 1.75;

    // 2. Inner mist disc and ripples
    float innerDisc = exp(-pow(r * 3.6, 2.0)) * 0.55;
    float microRipples = sin(r * 36.0 - t * 16.0) * exp(-pow(r * 2.5, 2.0)) * 0.22;

    // 3. Center muzzle flash
    float corePop = exp(-r * 11.5) * 1.85;

    // 4. Sample texture alpha mask
    vec4 texSample = texture(Sampler0, uv);
    float texLuma = texSample.r * texSample.a;

    float density = max(shockPeak + innerDisc + microRipples + corePop, texLuma * (1.1 + shockPeak * 0.9));

    // Smooth circular edge fade
    float edgeFade = 1.0 - smoothstep(0.86, 0.98, r);
    density *= edgeFade;

    // 5. Color theme (cyan or dark violet)
    vec3 baseTheme = vertexColor.rgb;
    bool isDarkTheme = (baseTheme.g < 0.35 && baseTheme.r < 0.45);
    vec3 crestColor = isDarkTheme ? vec3(0.28, 0.18, 0.36) : vec3(1.0, 1.0, 1.0);

    float crestMix = clamp(shockPeak * 0.85 + corePop * 0.75, 0.0, 1.0);
    vec3 color = mix(baseTheme, crestColor, crestMix);

    // 6. Final alpha and output
    float alpha = density * vertexColor.a * ColorModulator.a;
    color *= ColorModulator.rgb;

    if (alpha <= 0.003) {
        discard;
    }

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}

