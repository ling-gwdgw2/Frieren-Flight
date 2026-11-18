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
    vec2 shapeVec = (cloudType > 0.30 && cloudType < 0.75)
            ? centered * 1.04
            : vec2(centered.x * 0.88, centered.y * 1.12);
    float r = length(shapeVec);
    if (r > 1.0) discard;

    float t = GameTime * 1.8 + progress * 2.4 + seedVal * 7.9;
    float angle = atan(shapeVec.y, shapeVec.x);
    vec2 angDir = vec2(cos(angle), sin(angle));

    float swirl = fbm2(shapeVec * 2.8 + vec2(t * 0.18, -t * 0.26) + seedVal * 5.3);
    float detail = valNoise2(angDir * 5.2 + vec2(t * 0.4));
    float edgeWobble = (swirl - 0.5) * 0.36 + (detail - 0.5) * 0.15;

    float erode = smoothstep(0.45, 1.0, progress) * 0.32;
    float puffMask = 1.0 - smoothstep(0.44 - erode + edgeWobble, 0.94 - erode * 0.5 + edgeWobble * 0.25, r);
    if (cloudType > 0.75) {
        puffMask *= smoothstep(-0.98, -0.32, centered.y);
    }

    float celShade = smoothstep(0.34, 0.64, 0.45 + 0.55 * swirl - centered.y * 0.28);

    vec3 color;
    float alpha;

    if (cloudType > 0.75) {
        // Rolling khaki-taupe ground dust wall with strong warm blast inner bounce light
        vec3 shadowTaupe = vec3(0.32, 0.27, 0.21);
        vec3 litKhaki = vec3(0.66, 0.56, 0.43);
        vec3 warmBounce = vec3(1.06, 0.66, 0.24);

        float innerBounce = (1.0 - smoothstep(0.25, 0.78, progress)) * (1.0 - smoothstep(-0.25, 0.68, centered.y));
        color = mix(shadowTaupe, litKhaki, celShade);
        color = mix(color, warmBounce, clamp(innerBounce * 0.58, 0.0, 0.70));
        alpha = clamp(puffMask * (0.78 - 0.26 * progress) * fade * ColorModulator.a, 0.0, 1.0);
    } else if (cloudType > 0.30) {
        // Flying dark charcoal soot-smoke chunks mixed into rock burst
        vec3 darkSoot = vec3(0.14, 0.11, 0.09);
        vec3 litSootRim = vec3(0.86, 0.42, 0.12);
        float hotEdge = (1.0 - smoothstep(0.15, 0.65, progress)) * smoothstep(0.35, 0.85, swirl);
        color = mix(darkSoot, litSootRim, hotEdge * 0.65);
        alpha = clamp(puffMask * 0.78 * (1.0 - progress * 0.55) * fade * ColorModulator.a, 0.0, 1.0);
    } else {
        // Cream-white waist & crown condensation clouds with warm golden underglow
        vec3 peachShadow = vec3(0.92, 0.70, 0.44);
        vec3 brightCream = vec3(1.08, 1.03, 0.94);
        vec3 warmRim = vec3(1.12, 0.68, 0.22);
        vec3 cooledSmoke = vec3(0.54, 0.47, 0.41);

        vec3 activeVapor = mix(peachShadow, brightCream, celShade);
        activeVapor = mix(activeVapor, warmRim, clamp((-centered.y * 0.6 + 0.2) * (1.0 - smoothstep(0.15, 0.55, progress)) * 0.48, 0.0, 0.50));
        color = mix(activeVapor, cooledSmoke, smoothstep(0.45, 0.85, progress));
        alpha = clamp(puffMask * (0.94 - 0.24 * progress) * fade * ColorModulator.a, 0.0, 1.0);
    }

    if (alpha < 0.01) discard;
    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
