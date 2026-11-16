#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 uv;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);

    // 1. Fade smoothly before quad edges
    float streakY = abs(p.y);
    float streakX = abs(p.x);
    float edgeFadeX = smoothstep(1.0, 0.75, streakX);
    float edgeFadeY = smoothstep(1.0, 0.75, streakY);
    float quadMask = edgeFadeX * edgeFadeY;

    // 2. Core glow and soft outer halo
    float radialMask = 1.0 - smoothstep(0.85, 1.0, r);
    float core = exp(-pow(r * 4.5, 2.0)) * radialMask;
    float mantle = exp(-pow(r * 2.2, 1.8)) * 0.70 * radialMask;
    float aura = exp(-r * 3.5) * 0.40 * radialMask;

    // 3. Horizontal lens flare streak and cross glints
    float streak = exp(-pow(streakY * 18.0, 1.6)) * exp(-pow(streakX * 1.5, 2.0)) * 0.65 * quadMask;
    float glintH = exp(-streakY * 36.0) * exp(-streakX * 2.8) * 0.45 * quadMask;
    float glintV = exp(-streakX * 36.0) * exp(-streakY * 2.8) * 0.22 * quadMask;

    // 4. Red/blue color fringe along the streak
    float streakRed = exp(-pow(streakY * 15.0, 1.6)) * exp(-pow(streakX * 1.35, 2.0)) * 0.25 * quadMask;
    float streakBlue = exp(-pow(streakY * 21.0, 1.6)) * exp(-pow(streakX * 1.65, 2.0)) * 0.25 * quadMask;

    // 5. Glow texture sample (multiplied by alpha to avoid square edges)
    vec4 texSample = texture(Sampler0, uv);
    float texLuma = texSample.r * texSample.a * quadMask;

    float bloom = (core * 1.1 + mantle + aura + streak + glintH + glintV) * 0.60 + texLuma * 0.50;
    bloom *= quadMask;

    // 6. Color tint shifting to bright white at the center
    vec3 baseTint = vertexColor.rgb;
    vec3 whiteHot = vec3(1.0, 0.99, 0.96);

    vec3 color = mix(baseTint, whiteHot, smoothstep(0.35, 0.95, bloom));
    color += vec3(0.20, 0.05, 0.10) * streakRed;
    color += vec3(0.05, 0.15, 0.30) * streakBlue;

    // 7. Final alpha and output
    float alpha = bloom * vertexColor.a * ColorModulator.a;
    color *= ColorModulator.rgb;

    if (alpha <= 0.003) {
        discard;
    }

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
