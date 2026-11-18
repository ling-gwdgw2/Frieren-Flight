#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.54 * valNoise2(p);
    p = mat2(0.80, -0.60, 0.60, 0.80) * p * 2.05;
    v += 0.30 * valNoise2(p);
    p = mat2(0.80, -0.60, 0.60, 0.80) * p * 2.08;
    v += 0.16 * valNoise2(p);
    return v;
}

void main() {
    float progress = clamp(vColor.r, 0.0, 1.0);
    float seedVal = vColor.g;
    // type encoding:
    // < 0.30 : cream-white condensation clouds on dome (waist & crown collars)
    // 0.30..0.75 : flying dark soot-smoke chunks
    // > 0.75 : rolling khaki/taupe ground dust wave
    float cloudType = vColor.b;
    float fade = clamp(vColor.a, 0.0, 1.0);

    vec2 centered = vUv * 2.0 - 1.0;
    vec2 baseShape = (cloudType > 0.30 && cloudType < 0.75)
            ? centered * 1.05
            : vec2(centered.x * 0.94, centered.y * 1.08);
    float baseR = length(baseShape);
    if (baseR > 1.0) discard;

    float t = GameTime * 1.8 + progress * 2.2 + seedVal * 7.9;
    float angle = atan(baseShape.y, baseShape.x);
    vec2 angDir = vec2(cos(angle), sin(angle));

    // Domain-warped FBM creates billowing, non-oval vapor wisps that fray apart cleanly over time
    float warpA = fbm2(baseShape * 2.3 + vec2(t * 0.22, -t * 0.24) + seedVal * 5.3);
    float warpB = fbm2(baseShape * 3.8 + vec2(-t * 0.31, t * 0.27) + seedVal * 9.7);
    vec2 warpedPos = baseShape + vec2(warpA - 0.5, warpB - 0.5) * (0.28 + 0.16 * progress);
    float r = length(warpedPos);

    float swirl = fbm2(warpedPos * 2.7 + vec2(t * 0.19, -t * 0.26) + seedVal * 5.3);
    float wispDetail = valNoise2(angDir * 4.6 + vec2(t * 0.35, baseR * 2.4));
    float fbmDensity = clamp(swirl * 0.68 + wispDetail * 0.32, 0.0, 1.0);

    // Smooth radial Gaussian envelope (zero crisp oval edge) + progressive FBM erosion so smoke dissipates into thin tendrils
    float radialWindow = 1.0 - smoothstep(0.42, 0.98, baseR);
    float radialGaussian = exp(-r * r * (2.6 + 1.4 * progress)) * radialWindow;
    float dissolve = smoothstep(0.24, 0.96, progress);
    float puffMask = clamp((radialGaussian * (0.42 + 0.95 * fbmDensity) - dissolve * (0.34 * (1.0 - swirl * 0.55))), 0.0, 1.0);
    puffMask = smoothstep(0.02, 0.62, puffMask) * radialWindow;
    if (cloudType > 0.75) {
        puffMask *= smoothstep(-0.98, -0.28, centered.y);
    }

    float celShade = smoothstep(0.32, 0.68, 0.44 + 0.56 * swirl - centered.y * 0.26);

    vec3 color;
    float alpha;

    if (cloudType > 0.75) {
        // Rolling khaki-taupe ground dust mist with warm blast inner bounce light
        vec3 shadowTaupe = vec3(0.34, 0.29, 0.23);
        vec3 litKhaki = vec3(0.68, 0.58, 0.45);
        vec3 warmBounce = vec3(1.06, 0.68, 0.26);

        float innerBounce = (1.0 - smoothstep(0.22, 0.75, progress)) * (1.0 - smoothstep(-0.25, 0.68, centered.y));
        color = mix(shadowTaupe, litKhaki, celShade);
        color = mix(color, warmBounce, clamp(innerBounce * 0.56, 0.0, 0.68));
        float lifeFade = (1.0 - smoothstep(0.45, 0.98, progress));
        alpha = clamp(puffMask * 0.44 * (0.45 + 0.55 * lifeFade) * fade * ColorModulator.a, 0.0, 1.0);
    } else if (cloudType > 0.30) {
        // Wispy dark soot trails dissipating smoothly into warm haze
        vec3 darkSoot = vec3(0.16, 0.12, 0.10);
        vec3 litSootRim = vec3(0.88, 0.44, 0.14);
        float hotEdge = (1.0 - smoothstep(0.15, 0.62, progress)) * smoothstep(0.35, 0.85, swirl);
        color = mix(darkSoot, litSootRim, hotEdge * 0.65);
        float lifeFade = (1.0 - smoothstep(0.30, 0.92, progress));
        alpha = clamp(puffMask * 0.42 * lifeFade * fade * ColorModulator.a, 0.0, 1.0);
    } else {
        // Silky cream-white waist & crown condensation mist collars (seamless overlapping vapor instead of clumpy puffs)
        vec3 peachShadow = vec3(0.92, 0.72, 0.48);
        vec3 brightCream = vec3(1.08, 1.04, 0.96);
        vec3 warmRim = vec3(1.12, 0.70, 0.24);
        vec3 cooledMist = vec3(0.68, 0.62, 0.56);

        vec3 activeVapor = mix(peachShadow, brightCream, celShade);
        activeVapor = mix(activeVapor, warmRim, clamp((-centered.y * 0.6 + 0.2) * (1.0 - smoothstep(0.15, 0.55, progress)) * 0.48, 0.0, 0.50));
        color = mix(activeVapor, cooledMist, smoothstep(0.42, 0.85, progress));
        float lifeFade = (1.0 - smoothstep(0.38, 0.96, progress));
        alpha = clamp(puffMask * 0.48 * (0.35 + 0.65 * lifeFade) * fade * ColorModulator.a, 0.0, 1.0);
    }

    if (alpha < 0.006) discard;
    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
