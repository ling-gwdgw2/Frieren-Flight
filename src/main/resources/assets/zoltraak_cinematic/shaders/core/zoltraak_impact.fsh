#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 uv;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

// Hash and noise helpers
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.55 + noise(p * 2.15) * 0.30 + noise(p * 4.41) * 0.15;
}

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float theta = atan(p.y, p.x);

    // Smooth circular edge fade so the quad corners never show
    float edgeFade = 1.0 - smoothstep(0.55, 0.95, r);
    if (edgeFade <= 0.001) discard;

    // Decoded from vertexColor: progress, dark variant flag, surface vs cloud flag
    float progress  = clamp(vertexColor.r, 0.0, 1.0);
    bool  isBlack   = vertexColor.g > 0.5;
    bool  isSurface = vertexColor.b > 0.5;
    float fade      = vertexColor.a;

    float t = GameTime * 2.8;
    float density = 0.0;
    vec3 color = vec3(0.0);

    if (isSurface) {
        // Mode 1: Ground impact crater and scorch cracks
        float holeRim = exp(-pow((r - 0.22) * 12.0, 2.0)) * (1.0 - progress * 0.60) * 2.4;
        float cracks = pow(max(0.0, sin(theta * 8.0 + fbm(p * 5.0) * 3.0)), 6.0);
        float crackFade = exp(-r * 3.5) * (1.0 - progress * 0.50) * cracks * 1.8;
        float centerGlow = exp(-pow(r * 3.5, 2.0)) * exp(-progress * 4.0) * 2.8;

        density = (holeRim + crackFade + centerGlow) * edgeFade;

        if (!isBlack) {
            vec3 cyan = vec3(0.20, 0.90, 1.00);
            vec3 violet = vec3(0.50, 0.20, 0.95);
            vec3 white = vec3(1.00, 1.00, 1.00);
            color = mix(violet, cyan, smoothstep(0.15, 0.65, density));
            color = mix(color, white, smoothstep(0.65, 1.30, density));
        } else {
            vec3 voidBlack = vec3(0.003, 0.004, 0.008);
            vec3 soot = vec3(0.045, 0.035, 0.055);
            vec3 ash = vec3(0.35, 0.22, 0.48);
            color = mix(voidBlack, soot, smoothstep(0.12, 0.60, density));
            color = mix(color, ash, smoothstep(0.60, 1.25, density));
        }
    } else {
        // Mode 2: Expanding 3D smoke and blast cloud
        float flashDecay = exp(-progress * 8.5);
        float coreFlash = exp(-pow(r * 4.5, 2.0)) * flashDecay * 3.5;

        vec2 smokeUV = p * (2.2 - progress * 0.5) - vec2(0.0, progress * 0.5);
        float smokeNoise = fbm(smokeUV + vec2(t * 0.5, -t * 0.25));
        float smokeRadius = 0.38 + 0.52 * pow(progress, 0.45);
        float smokeShape = (r / max(smokeRadius, 0.01)) + (smokeNoise - 0.5) * 0.40;
        float smoke = (1.0 - smoothstep(0.48, 0.88, smokeShape)) * (1.10 - progress * 0.65);
        float smokeShade = smoothstep(0.35, 0.70, smokeNoise + (0.4 - p.y) * 0.45);

        // Flying sparks
        vec2 emberGrid = (p + vec2(0.0, -progress * 0.3)) * 10.0;
        vec2 cell = floor(emberGrid);
        vec2 cellFract = fract(emberGrid) - 0.5;
        float cellSeed = hash(cell);
        float ember = (1.0 - smoothstep(0.04, 0.28, length(cellFract))) * step(0.65, cellSeed);
        ember *= exp(-r * 1.5) * (1.0 - progress * 0.55) * (0.7 + 0.3 * sin(cellSeed * 50.0 + t * 8.0));

        density = (coreFlash + smoke * 1.35 + ember * 0.85) * edgeFade;

        if (!isBlack) {
            vec3 white     = vec3(1.20, 1.20, 1.20);
            vec3 cyan      = vec3(0.20, 0.92, 1.00);
            vec3 violet    = vec3(0.48, 0.18, 0.92);
            vec3 smokeHi   = vec3(0.85, 0.98, 0.95);
            vec3 smokeLo   = vec3(0.38, 0.62, 0.75);

            vec3 smokeColor = mix(smokeLo, smokeHi, smokeShade);
            vec3 beamColor = mix(violet, cyan, smoothstep(0.20, 0.68, density));
            beamColor = mix(beamColor, white, smoothstep(0.68, 1.30, density));

            color = mix(smokeColor, beamColor, clamp((coreFlash + smoke * 0.7) * 1.2, 0.0, 1.0));
        } else {
            vec3 voidBlack = vec3(0.015, 0.008, 0.032);
            vec3 sootLo    = vec3(0.08, 0.035, 0.14);
            vec3 sootHi    = vec3(0.24, 0.10, 0.40);
            vec3 ash       = vec3(0.62, 0.28, 0.92);

            vec3 sootColor = mix(sootLo, sootHi, smokeShade);
            color = mix(voidBlack, sootColor, smoothstep(0.10, 0.52, density));
            color = mix(color, ash, smoothstep(0.45, 1.05, density));
        }
    }

    float alpha = clamp(density * fade * ColorModulator.a, 0.0, 1.0);
    if (isBlack && !isSurface) {
        alpha *= mix(0.88, 0.45, progress);
    }
    color *= ColorModulator.rgb;

    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
