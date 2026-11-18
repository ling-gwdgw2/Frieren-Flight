#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;

in vec2 uv;
in vec3 viewPosition;
in float age;
in float energy;
in float span;
in float halfWidth;

out vec4 fragColor;

// Clean analytic noise
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2D(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
        mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

void main() {
    bool isVolume = uv.y >= 2.0;
    float x = uv.x;
    float y = isVolume ? sin((uv.y - 2.0) * 6.2831853) * 0.28 : (uv.y * 2.0 - 1.0);

    if (x < 0.0) discard;

    // 1. Surface normal and view angle for 3D beam cylinder (guarded against zero-length vectors)
    vec3 crossP = cross(dFdx(viewPosition), dFdy(viewPosition));
    float crossLen = length(crossP);
    vec3 normal = crossLen > 0.0001 ? crossP / crossLen : vec3(0.0, 0.0, 1.0);
    float viewLen = length(viewPosition);
    vec3 viewDir = viewLen > 0.0001 ? -viewPosition / viewLen : vec3(0.0, 0.0, 1.0);
    float facing = clamp(abs(dot(normal, viewDir)), 0.0, 1.0);

    // 2. Flowing void waves
    float flow = noise2D(vec2(x * 24.0 - age * 0.9, y * 7.5));
    float pulse = sin(x * 35.0 - age * 2.1);

    // 3. Beam thickness profile
    float beamRadius = 0.26 + 0.06 * flow + 0.015 * pulse;
    float dist = isVolume ? (1.05 - 0.72 * pow(facing, 0.65)) : (abs(y) / max(beamRadius, 0.01));

    // 4. Black inner core
    float voidCore = 1.0 - smoothstep(0.78, 0.98, dist);

    // 5. Violet edge glow and sparks
    float rim = exp(-pow((dist - 1.0) * 16.0, 2.0));
    float sparks = pow(noise2D(vec2(x * 150.0 - age * 4.5, y * 32.0)), 6.0) * rim * 1.6;

    // 6. Color gradient: black core to violet edge
    vec3 voidBlack   = vec3(0.005, 0.003, 0.009);
    vec3 darkPurple  = vec3(0.08, 0.04, 0.12);
    vec3 violet      = vec3(0.55, 0.20, 0.82);
    vec3 lightViolet = vec3(0.88, 0.82, 0.98);

    vec3 color = mix(darkPurple, violet, clamp(rim * 1.4, 0.0, 1.0));
    color = mix(color, lightViolet, clamp(sparks * 1.4, 0.0, 1.0));
    color = mix(color, voidBlack, voidCore);

    if (isVolume) {
        color += darkPurple * pow(facing, 2.0);
    }

    // 7. Final alpha
    float alpha = max(voidCore * 0.98, rim * 0.88 + sparks) * energy;
    if (!isVolume) {
        alpha *= (1.0 - voidCore * 0.2);
    }
    alpha *= ColorModulator.a;
    if (alpha < 0.003) discard;

    fragColor = vec4(color * ColorModulator.rgb, clamp(alpha, 0.0, 1.0));
}
