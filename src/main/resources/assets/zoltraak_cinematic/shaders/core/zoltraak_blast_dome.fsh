#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 vUv;
in vec3 vEyeNormal;
in vec3 vViewDir;
in vec3 vLocalDir;
in vec4 vColor;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);

    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

float fbm3(vec3 p) {
    float sum = 0.52 * valNoise3(p);
    p = p * 2.03 + vec3(13.1, 7.7, 19.3);
    sum += 0.30 * valNoise3(p);
    p = p * 2.07 + vec3(5.3, 17.9, 11.1);
    sum += 0.18 * valNoise3(p);
    return sum;
}

float ridgedPlasma(vec3 p) {
    float n1 = 1.0 - abs(valNoise3(p) * 2.0 - 1.0);
    float n2 = 1.0 - abs(valNoise3(p * 2.15 + vec3(4.2, -3.1, 6.7)) * 2.0 - 1.0);
    return clamp(n1 * 0.62 + n2 * 0.38, 0.0, 1.0);
}

void main() {
    float eyeLen = length(vEyeNormal);
    vec3 E = eyeLen > 0.0001 ? vEyeNormal / eyeLen : vec3(0.0, 0.0, 1.0);
    float viewLen = length(vViewDir);
    vec3 V = viewLen > 0.0001 ? vViewDir / viewLen : vec3(0.0, 0.0, 1.0);
    float dirLen = length(vLocalDir);
    vec3 L = dirLen > 0.0001 ? vLocalDir / dirLen : vec3(0.0, 1.0, 0.0);

    float progress = clamp(vColor.r, 0.0, 1.0);
    bool isBlack = vColor.g > 0.5;
    float shellKind = vColor.b; // 0.0 = outer shockwave dome, 0.5 = inner plasma flash dome, 1.0 = base Mach surge ring
    float fade = clamp(vColor.a, 0.0, 1.0);

    float animTime = GameTime * 3.5 + progress * 4.5;
    float elev = clamp(vUv.y, 0.0, 1.0);

    // Seam-free 3D plasma turbulence across spherical direction L
    float flowScale = shellKind > 0.3 && shellKind < 0.7 ? 4.6 : 3.5;
    vec3 flowPos = L * flowScale + vec3(0.0, -animTime * 0.65, 0.0);
    float warp = fbm3(flowPos * 1.15);
    float surfNoise = fbm3(flowPos + vec3(warp * 0.90));
    float ridge = pow(ridgedPlasma(L * 5.6 + vec3(warp * 1.5, -animTime * 1.1, warp * 0.9)), 3.0);
    float fineArc = pow(ridgedPlasma(L * 11.2 - vec3(animTime * 1.5, warp * 1.8, -animTime * 0.8)), 5.8);

    // Heat-warped optical Fresnel normal modulation
    float rawDot = dot(E, V);
    float perturbedDot = clamp(abs(rawDot) + (surfNoise - 0.5) * 0.16 * (1.0 - 0.35 * progress), 0.0, 1.0);
    float frontOnly = smoothstep(-0.08, 0.14, rawDot);
    float backMix = mix(0.22, 1.0, frontOnly);

    float fresnelBroad = pow(1.0 - perturbedDot, 3.2);
    float fresnelSharp = pow(1.0 - perturbedDot, 5.6);
    float grazingEdge = exp(-pow(perturbedDot * 10.5, 2.0));
    float silhouette = (fresnelBroad * 0.42 + fresnelSharp * 0.88 + grazingEdge * 0.90) * backMix;

    // Travelling shockwave compression ripples sweeping top-to-ground
    float pressurePhase = elev * 13.0 - progress * 24.0 + surfNoise * 2.6;
    float waveBand = pow(max(0.0, cos(pressurePhase)), 7.0);
    float shockFront = exp(-pow((elev - mix(0.92, 0.10, clamp(progress * 1.65, 0.0, 1.0))) * 7.2, 2.0));
    float baseContactRim = exp(-pow((elev - 0.07) * 14.0, 2.0)) * (1.0 - 0.35 * progress);

    float intensity = 0.0;
    float alpha = 0.0;
    vec3 color = vec3(0.0);

    if (shellKind > 0.8) {
        // Mode: Soft low ground Mach condensation / dust surge collar (no sharp upper cone seam!)
        float skirtProfile = sin(elev * 3.14159265);
        float skirtFeather = pow(max(0.0, skirtProfile), 1.45);
        float vaporClouds = smoothstep(0.28, 0.88, surfNoise + ridge * 0.32);
        intensity = skirtFeather * (0.45 + 0.95 * vaporClouds + waveBand * 0.40) * backMix;
        alpha = clamp(intensity * (0.85 - 0.35 * progress) * fade * ColorModulator.a, 0.0, 1.0);

        if (isBlack) {
            vec3 ashVoid = vec3(0.02, 0.01, 0.045);
            vec3 violetSurge = vec3(0.52, 0.15, 0.92);
            vec3 brightRim = vec3(0.92, 0.68, 1.00);
            color = mix(ashVoid, violetSurge, clamp(vaporClouds * 0.90 + waveBand * 0.55, 0.0, 1.0));
            color = mix(color, brightRim, clamp(ridge * 0.85 + grazingEdge * 0.65, 0.0, 1.0));
        } else {
            vec3 mistSteel = vec3(0.30, 0.50, 0.78);
            vec3 cyanSurge = vec3(0.42, 0.90, 1.00);
            vec3 brightVapor = vec3(0.96, 0.99, 1.00);
            color = mix(mistSteel, cyanSurge, clamp(vaporClouds * 0.88 + waveBand * 0.50, 0.0, 1.0));
            color = mix(color, brightVapor, clamp(ridge * 0.80 + (1.0 - progress) * 0.35, 0.0, 1.0));
        }
    } else if (shellKind > 0.3) {
        // Mode: Early ignition plasma flash containment dome (rapidly breaks into fiery tendrils)
        float domeFeather = smoothstep(0.0, 0.14, elev);
        float breakup = smoothstep(0.15, 0.85, surfNoise + ridge * 0.45 - progress * 0.65);
        float plasmaVeins = ridge * 1.25 + fineArc * 1.60;
        intensity = (silhouette * 0.70 + plasmaVeins * 1.05 + baseContactRim * 0.50) * domeFeather * mix(1.0, breakup, smoothstep(0.15, 0.55, progress));
        alpha = clamp(intensity * fade * ColorModulator.a, 0.0, 1.0);

        if (isBlack) {
            vec3 abyssVoid = vec3(0.012, 0.006, 0.028);
            vec3 deepViolet = vec3(0.42, 0.09, 0.80);
            vec3 neonMagenta = vec3(0.84, 0.34, 1.00);
            vec3 whiteTear = vec3(1.00, 0.90, 1.00);
            color = mix(abyssVoid, deepViolet, clamp(fresnelBroad * backMix + waveBand * 0.4, 0.0, 1.0));
            color = mix(color, neonMagenta, clamp(silhouette * 0.85 + ridge * 1.10, 0.0, 1.0));
            color = mix(color, whiteTear, clamp(fineArc * 1.20 + grazingEdge * 0.80, 0.0, 1.0));
        } else {
            vec3 deepCobalt = vec3(0.08, 0.28, 0.88);
            vec3 electricCyan = vec3(0.24, 0.88, 1.00);
            vec3 whiteArc = vec3(0.98, 1.00, 1.00);
            color = mix(deepCobalt, electricCyan, clamp(silhouette * 0.90 + waveBand * 0.5, 0.0, 1.0));
            color = mix(color, whiteArc, clamp(plasmaVeins * 0.90 + grazingEdge * 0.85, 0.0, 1.0));
        }
    } else {
        // Mode: Outer supersonic shockwave dome (clear center, razor-sharp Fresnel rim & shock veins)
        float domeFeather = smoothstep(0.0, 0.16, elev);
        float tendrils = (ridge * 0.65 + fineArc * 1.15) * (0.28 + 0.72 * fresnelBroad);
        float shellRipple = (waveBand * 0.36 + shockFront * 0.64) * (0.25 + 0.75 * surfNoise) * (0.35 + 0.65 * fresnelBroad);
        intensity = (silhouette * 0.78 + tendrils * 0.90 + shellRipple + baseContactRim * 0.68) * domeFeather;
        alpha = clamp(intensity * fade * ColorModulator.a, 0.0, 1.0);

        if (isBlack) {
            vec3 voidShadow = vec3(0.02, 0.01, 0.045);
            vec3 violetPulse = vec3(0.52, 0.16, 0.92);
            vec3 lavenderRim = vec3(0.92, 0.68, 1.00);
            color = mix(voidShadow, violetPulse, clamp(silhouette * 0.90 + shellRipple, 0.0, 1.0));
            color = mix(color, lavenderRim, clamp(tendrils * 1.15 + grazingEdge * 0.90 + baseContactRim * 0.75, 0.0, 1.0));
        } else {
            vec3 indigoEdge = vec3(0.12, 0.34, 0.94);
            vec3 electricCyan = vec3(0.32, 0.88, 1.00);
            vec3 whiteArc = vec3(0.96, 0.99, 1.00);
            color = mix(indigoEdge, electricCyan, clamp(silhouette * 0.88 + shellRipple, 0.0, 1.0));
            color = mix(color, whiteArc, clamp(tendrils * 1.10 + grazingEdge * 0.90 + baseContactRim * 0.72, 0.0, 1.0));
        }
    }

    if (alpha < 0.004) discard;
    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
