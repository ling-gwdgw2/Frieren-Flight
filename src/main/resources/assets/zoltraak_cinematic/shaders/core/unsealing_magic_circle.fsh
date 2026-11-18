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
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    vec2 dir = r > 0.001 ? p / r : vec2(0.0);
    float theta = atan(p.y, p.x);
    float t = GameTime * 2.5;

    // 1. Heat shimmer UV warp
    vec2 heatDistort = vec2(
        sin(uv.y * 22.0 + t * 4.2 + uv.x * 8.0),
        cos(uv.x * 22.0 - t * 3.6 + uv.y * 8.0)
    ) * 0.0055;
    vec2 uvDistorted = uv + heatDistort;

    // 2. Crisp unshifted rune luminance with tight subtle RGB edge fringe
    float distOffset = 0.0009;
    vec2 uv_r = uvDistorted - dir * distOffset;
    vec2 uv_g = uvDistorted;
    vec2 uv_b = uvDistorted + dir * distOffset;

    vec4 centerTex = texture(Sampler0, uvDistorted);
    float rawR = texture(Sampler0, uv_r).r;
    float rawG = texture(Sampler0, uv_g).g;
    float rawB = texture(Sampler0, uv_b).b;
    float rawA = centerTex.a;

    float rawLuma = max(centerTex.r, max(centerTex.g, centerTex.b)) * rawA;
    float luma = clamp(rawLuma * 0.82 + smoothstep(0.08, 0.45, rawLuma) * 0.34, 0.0, 1.0);
    if (luma <= 0.003) {
        discard;
    }

    // 3. 8-point star flame wave
    float starFlare = pow(abs(cos(theta * 4.0)), 3.0);
    float flameTongue = starFlare * sin(r * 18.0 - t * 5.5) * 0.28;

    // 4. Shimmer pulse across runes
    float pulse = 0.88 + 0.12 * sin(t * 3.2 + r * 10.0);

    // 5. Fire colors: crimson -> gold -> white core
    vec3 emberRed = vec3(1.0, 0.28, 0.06);
    vec3 goldenAmber = vec3(1.0, 0.76, 0.22);
    vec3 whiteHot = vec3(1.0, 0.98, 0.92);

    vec3 runeColor = mix(emberRed, goldenAmber, smoothstep(0.15, 0.70, luma));
    runeColor = mix(runeColor, whiteHot, smoothstep(0.70, 0.98, luma));

    // Apply tint and pulse
    vec3 color = runeColor * (1.0 + max(0.0, flameTongue)) * pulse * vertexColor.rgb * ColorModulator.rgb;
    float alpha = luma * vertexColor.a * ColorModulator.a;

    if (alpha <= 0.003) {
        discard;
    }

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
