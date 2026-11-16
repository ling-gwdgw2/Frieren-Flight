#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;

    float theta = atan(p.y, p.x);
    float t = (GameTime > 0.001 ? GameTime : 1.0) * 1.3;

    float castProgress = clamp(vColor.r, 0.0, 1.0);
    float pulse = clamp(vColor.g, 0.0, 1.0);
    float fade = clamp(vColor.a, 0.0, 1.0);

    // 1. Faster spin near center
    float rotSpeed = t * (0.22 / sqrt(max(r, 0.08)));
    float localTheta = theta - rotSpeed;

    // 2. Dark center cutout
    float horizonMask = smoothstep(0.125, 0.155, r);

    // 3. Bright inner photon ring
    float photonRing = exp(-pow((r - 0.165) * 110.0, 2.0));
    float photonHaze = exp(-pow((r - 0.165) * 20.0, 2.0)) * 0.45;

    // 4. Swirling spiral arms
    float spiral1 = sin(localTheta * 3.0 + 9.5 * log(max(r, 0.02)) - t * 0.8) * 0.5 + 0.5;
    float spiral2 = sin(localTheta * 6.0 + 15.0 * log(max(r, 0.02)) + t * 0.5) * 0.5 + 0.5;
    float armPattern = pow(spiral1 * 0.7 + spiral2 * 0.3, 2.2);

    float diskEnvelope = smoothstep(0.16, 0.22, r) * exp(-pow(max(0.0, r - 0.22) * 2.6, 1.7));
    float accretionFlow = armPattern * diskEnvelope * (0.85 + 0.45 * castProgress);

    // 5. Brighter approaching left side
    float sideBoost = 1.0 + 0.65 * sin(theta) * smoothstep(0.16, 0.70, r);

    // 6. Target range rings
    // Inner disk ring (r ~ 0.28)
    float iscoRing = exp(-pow((r - 0.28) * 85.0, 2.0)) * 0.80;
    // Gravity boundary ring (r ~ 0.45)
    float shadowRing = exp(-pow((r - 0.45) * 80.0, 2.0)) * 0.70;
    // Main blast radius ring (r ~ 0.72 = 32 blocks)
    float blastRing = exp(-pow((r - 0.72) * 75.0, 2.0)) * 1.35;
    float blastInnerGlow = exp(-pow((r - 0.72) * 18.0, 2.0)) * 0.35;
    // Outer pull ring (r ~ 0.95)
    float outerRing = exp(-pow((r - 0.95) * 60.0, 2.0)) * 0.55;

    // 7. Range tick marks and crosshair guides
    float fineTicks = pow(max(0.0, cos(theta * 36.0)), 18.0) * smoothstep(0.68, 0.72, r) * (1.0 - smoothstep(0.72, 0.76, r));
    float majorTicks = pow(max(0.0, cos(theta * 8.0)), 28.0) * smoothstep(0.25, 0.75, r) * (1.0 - smoothstep(0.73, 0.75, r));
    float crossAxes = (pow(max(0.0, cos(theta * 2.0)), 64.0) + pow(max(0.0, sin(theta * 2.0)), 64.0))
                      * smoothstep(0.18, 0.95, r) * 0.25;

    // 8. Expanding charge pulses
    float shockPhase = fract(r * 4.0 - t * (1.4 + castProgress * 2.2));
    float shockPulse = exp(-pow(shockPhase * 4.5, 2.0)) * (0.2 + 0.65 * castProgress)
                       * smoothstep(0.18, 0.72, r) * (1.0 - smoothstep(0.72, 0.85, r));

    // 9. Combine brightness
    float baseLuma = (photonRing * 1.6 + photonHaze * 0.8
                      + accretionFlow * 1.4
                      + iscoRing + shadowRing + blastRing + blastInnerGlow + outerRing
                      + fineTicks * 1.1 + majorTicks * 1.3 + crossAxes
                      + shockPulse) * sideBoost * horizonMask;

    // Smooth outer edge
    float edgeCut = 1.0 - smoothstep(0.98, 1.00, r);
    float totalAlpha = clamp(baseLuma * fade * pulse * edgeCut * ColorModulator.a, 0.0, 1.0);

    if (totalAlpha < 0.005) discard;

    // 10. Amber and gold palette
    vec3 deepAmber   = vec3(0.96, 0.42, 0.06);
    vec3 radiantGold = vec3(1.00, 0.78, 0.24);
    vec3 whiteHot    = vec3(1.00, 0.98, 0.92);

    vec3 color = mix(deepAmber, radiantGold, clamp(baseLuma * 0.65, 0.0, 1.0));
    color = mix(color, whiteHot, clamp((photonRing * 1.4 + blastRing * 0.7 + majorTicks) * 0.8, 0.0, 1.0));

    // Boost white-hot emission as spell cast completes
    color = mix(color, whiteHot, castProgress * 0.45);

    fragColor = vec4(color * ColorModulator.rgb, totalAlpha);
}

