#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// Pass 2: Multi-Octave HDR Bloom Mip Downsample + Gaussian Filter (Modeled after Gargantua buffer/bufferB-D.glsl)

uniform sampler2D SourceSampler;
uniform vec4 BloomTexel;

in vec2 vUv;
out vec4 fragColor;

vec3 fetchHdr(vec2 coord) {
    return max(vec3(0.0), texture(SourceSampler, clamp(coord, 0.0005, 0.9995)).rgb);
}

float karisWeight(vec3 c) {
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return 1.0 / (1.0 + luma * 0.12);
}

void main() {
    vec2 texel = BloomTexel.xy * max(BloomTexel.w, 0.25);
    float mipLevel = BloomTexel.z;

    // 13-tap energy-preserving bilinear downsample kernel
    vec3 a = fetchHdr(vUv + texel * vec2(-2.0,  2.0));
    vec3 b = fetchHdr(vUv + texel * vec2( 0.0,  2.0));
    vec3 c = fetchHdr(vUv + texel * vec2( 2.0,  2.0));

    vec3 d = fetchHdr(vUv + texel * vec2(-2.0,  0.0));
    vec3 e = fetchHdr(vUv);
    vec3 f = fetchHdr(vUv + texel * vec2( 2.0,  0.0));

    vec3 g = fetchHdr(vUv + texel * vec2(-2.0, -2.0));
    vec3 h = fetchHdr(vUv + texel * vec2( 0.0, -2.0));
    vec3 i = fetchHdr(vUv + texel * vec2( 2.0, -2.0));

    vec3 j = fetchHdr(vUv + texel * vec2(-1.0,  1.0));
    vec3 k = fetchHdr(vUv + texel * vec2( 1.0,  1.0));
    vec3 l = fetchHdr(vUv + texel * vec2(-1.0, -1.0));
    vec3 m = fetchHdr(vUv + texel * vec2( 1.0, -1.0));

    vec3 downCol;
    if (mipLevel < 0.5) {
        // First mip level: partial Karis weighting prevents sub-pixel firefly flicker on thin filaments
        vec3 g0 = (a + b + d + e) * 0.25;
        vec3 g1 = (b + c + e + f) * 0.25;
        vec3 g2 = (d + e + g + h) * 0.25;
        vec3 g3 = (e + f + h + i) * 0.25;
        vec3 g4 = (j + k + l + m) * 0.25;

        float w0 = karisWeight(g0) * 0.125;
        float w1 = karisWeight(g1) * 0.125;
        float w2 = karisWeight(g2) * 0.125;
        float w3 = karisWeight(g3) * 0.125;
        float w4 = karisWeight(g4) * 0.500;
        float wSum = max(w0 + w1 + w2 + w3 + w4, 1.0e-5);
        downCol = (g0 * w0 + g1 * w1 + g2 * w2 + g3 * w3 + g4 * w4) / wSum;
    } else {
        downCol = e * 0.125;
        downCol += (a + c + g + i) * 0.03125;
        downCol += (b + d + f + h) * 0.0625;
        downCol += (j + k + l + m) * 0.125;
    }

    // Wide Gaussian cross taps using exact bilinear offsets & weights from Gargantua buffer/bufferC.glsl & bufferD.glsl
    const float w1 = 0.29675293;
    const float w2 = 0.09442139;
    const float w3 = 0.01037598;
    const float o1 = 1.41176471 * 0.5;
    const float o2 = 3.29411765 * 0.5;
    const float o3 = 5.17647059 * 0.5;

    vec3 gaussRing = (
        fetchHdr(vUv + vec2(o1 * texel.x, 0.0)) + fetchHdr(vUv - vec2(o1 * texel.x, 0.0)) +
        fetchHdr(vUv + vec2(0.0, o1 * texel.y)) + fetchHdr(vUv - vec2(0.0, o1 * texel.y))
    ) * (w1 * 0.25) + (
        fetchHdr(vUv + vec2(o2 * texel.x, 0.0)) + fetchHdr(vUv - vec2(o2 * texel.x, 0.0)) +
        fetchHdr(vUv + vec2(0.0, o2 * texel.y)) + fetchHdr(vUv - vec2(0.0, o2 * texel.y))
    ) * (w2 * 0.25) + (
        fetchHdr(vUv + vec2(o3 * texel.x, 0.0)) + fetchHdr(vUv - vec2(o3 * texel.x, 0.0)) +
        fetchHdr(vUv + vec2(0.0, o3 * texel.y)) + fetchHdr(vUv - vec2(0.0, o3 * texel.y))
    ) * (w3 * 0.25);

    float gaussNorm = 0.19638062 + (w1 + w2 + w3) * 2.0;
    vec3 blurred = (downCol * 0.19638062 + gaussRing * 2.0) / gaussNorm;

    fragColor = vec4(mix(downCol, blurred, 0.65), 1.0);
}
