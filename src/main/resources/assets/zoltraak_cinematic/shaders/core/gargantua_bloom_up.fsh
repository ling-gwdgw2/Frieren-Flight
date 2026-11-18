#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.
// Pass 3: Bicubic B-Spline + 9-Tap Tent Bloom Upsample Pyramid

uniform sampler2D HighMipSampler;
uniform sampler2D LowMipSampler;
uniform vec4 UpsampleParams;

in vec2 vUv;
out vec4 fragColor;

// Standard uniform cubic B-spline basis weights
vec4 cubic(float x) {
    float x2 = x * x;
    float x3 = x2 * x;
    vec4 w;
    w.x =   -x3 + 3.0 * x2 - 3.0 * x + 1.0;
    w.y =  3.0 * x3 - 6.0 * x2       + 4.0;
    w.z = -3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0;
    w.w =  x3;
    return w / 6.0;
}

vec3 bicubicFetch(sampler2D tex, vec2 coord, vec2 invRes) {
    vec2 res = 1.0 / max(invRes, vec2(1.0e-5));
    coord = clamp(coord, 0.001, 0.999) * res;

    float fx = fract(coord.x);
    float fy = fract(coord.y);
    coord.x -= fx;
    coord.y -= fy;

    fx -= 0.5;
    fy -= 0.5;

    vec4 xcubic = cubic(fx);
    vec4 ycubic = cubic(fy);

    vec4 c = vec4(coord.x - 0.5, coord.x + 1.5, coord.y - 0.5, coord.y + 1.5);
    vec4 s = vec4(xcubic.x + xcubic.y, xcubic.z + xcubic.w, ycubic.x + ycubic.y, ycubic.z + ycubic.w);
    vec4 offset = c + vec4(xcubic.y, xcubic.w, ycubic.y, ycubic.w) / max(s, vec4(1.0e-5));

    vec3 sample0 = texture(tex, clamp(vec2(offset.x, offset.z) * invRes, 0.0005, 0.9995)).rgb;
    vec3 sample1 = texture(tex, clamp(vec2(offset.y, offset.z) * invRes, 0.0005, 0.9995)).rgb;
    vec3 sample2 = texture(tex, clamp(vec2(offset.x, offset.w) * invRes, 0.0005, 0.9995)).rgb;
    vec3 sample3 = texture(tex, clamp(vec2(offset.y, offset.w) * invRes, 0.0005, 0.9995)).rgb;

    float sx = s.x / max(s.x + s.y, 1.0e-5);
    float sy = s.z / max(s.z + s.w, 1.0e-5);

    return mix(mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);
}

vec3 tentUpsample(sampler2D tex, vec2 uv, vec2 d) {
    vec3 s = vec3(0.0);
    s += bicubicFetch(tex, uv + vec2(-d.x,  d.y), d);
    s += bicubicFetch(tex, uv + vec2( 0.0,  d.y), d) * 2.0;
    s += bicubicFetch(tex, uv + vec2( d.x,  d.y), d);
    s += bicubicFetch(tex, uv + vec2(-d.x,  0.0), d) * 2.0;
    s += bicubicFetch(tex, uv,                    d) * 4.0;
    s += bicubicFetch(tex, uv + vec2( d.x,  0.0), d) * 2.0;
    s += bicubicFetch(tex, uv + vec2(-d.x, -d.y), d);
    s += bicubicFetch(tex, uv + vec2( 0.0, -d.y), d) * 2.0;
    s += bicubicFetch(tex, uv + vec2( d.x, -d.y), d);
    return s * (1.0 / 16.0);
}

void main() {
    vec2 lowTexel = UpsampleParams.xy;
    float highWeight = UpsampleParams.z;
    float lowWeight = UpsampleParams.w;

    vec3 highCol = texture(HighMipSampler, vUv).rgb;
    vec3 lowCol = tentUpsample(LowMipSampler, vUv, lowTexel);

    fragColor = vec4(highCol * highWeight + lowCol * lowWeight, 1.0);
}
