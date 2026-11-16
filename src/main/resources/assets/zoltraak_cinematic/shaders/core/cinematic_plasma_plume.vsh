#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

// Zoltraak Cinematic - 100% Clean Room Great Zoltraak Plume Vertex Shader
// Transforms lateral turbulent plume geometry along beam trajectory

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out float age;
out float energy;
out float dark;
out float luminous;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9995);
    uv = UV0;
    age = Color.r * 255.0;
    dark = Color.g;
    luminous = Color.b;
    energy = Color.a;
}
