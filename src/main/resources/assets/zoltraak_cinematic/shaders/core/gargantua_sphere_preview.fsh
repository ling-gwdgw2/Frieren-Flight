#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec3 vEyeNormal;
in vec3 vViewDir;
in vec4 vColor;

out vec4 fragColor;

void main() {
    float eyeLen = length(vEyeNormal);
    vec3 E = eyeLen > 0.0001 ? vEyeNormal / eyeLen : vec3(0.0, 0.0, 1.0);
    float viewLen = length(vViewDir);
    vec3 V = viewLen > 0.0001 ? vViewDir / viewLen : vec3(0.0, 0.0, 1.0);

    // Use unsigned facing for rim curves and fade back-face Fresnel so NO_CULL cannot fill the disc solid white.
    float rawDot = dot(E, V);
    float ndotv = clamp(abs(rawDot), 0.0, 1.0);
    float frontOnly = smoothstep(-0.06, 0.08, rawDot);
    float backMix = mix(0.28, 1.0, frontOnly);

    float t = (GameTime > 0.001 ? GameTime : 1.0) * 1.4;

    float castProgress = clamp(vColor.r, 0.0, 1.0);
    float pulse = clamp(vColor.g, 0.0, 1.0);
    float fade = clamp(vColor.a, 0.0, 1.0);

    // 1. Edge rim glow (front hemisphere + grazing silhouette only)
    float fresnel = pow(1.0 - ndotv, 3.2);
    float edgeRim = exp(-pow(ndotv * 14.0, 2.0));
    float rimRipples = (sin(pow(1.0 - ndotv, 0.75) * 38.0 - t * 2.0) * 0.5 + 0.5) * fresnel * 0.40;
    float rimTerm = (edgeRim * 1.15 + fresnel * 0.55 + rimRipples) * frontOnly;

    // 2. Twisted sphere grid lines from spherical UV (pole- and seam-safe)
    float phi = (vUv.y - 0.5) * 3.14159265;
    float ny = sin(phi);
    float lambda = vUv.x * 6.283185307;

    float lambdaTwisted = lambda + sin(phi * 2.4) * 0.85 - t * 0.40;

    float latGrid = pow(max(0.0, cos(phi * 16.0)), 36.0);
    float lonGrid = pow(max(0.0, cos(lambdaTwisted * 12.0)), 28.0);
    float curvatureGrid = (latGrid + lonGrid) * (0.45 + 0.55 * fresnel);

    // 3. Equator and polar rings
    float equatorSeam = exp(-pow(ny * 32.0, 2.0)) * 1.15;
    float polarRings = (exp(-pow((abs(ny) - 0.70) * 35.0, 2.0)) + exp(-pow((abs(ny) - 0.90) * 45.0, 2.0))) * 0.55;
    float gridTerm = (curvatureGrid * 0.95 + equatorSeam + polarRings) * backMix;

    // 4. Final intensity
    float intensity = (rimTerm + gridTerm) * (0.85 + 0.50 * castProgress);

    float alpha = clamp(intensity * fade * pulse * ColorModulator.a, 0.0, 1.0);
    if (alpha < 0.005) discard;

    // 5. Amber and gold palette
    vec3 deepAmber   = vec3(0.96, 0.45, 0.08);
    vec3 radiantGold = vec3(1.00, 0.80, 0.28);
    vec3 whiteHot    = vec3(1.00, 0.98, 0.92);

    vec3 color = mix(deepAmber, radiantGold, clamp(fresnel * frontOnly * 1.1 + curvatureGrid * 0.55, 0.0, 1.0));
    color = mix(color, whiteHot, clamp(edgeRim * frontOnly * 1.15 + equatorSeam * backMix * 0.65 + castProgress * 0.30, 0.0, 1.0));

    fragColor = vec4(color * ColorModulator.rgb, alpha);
}

