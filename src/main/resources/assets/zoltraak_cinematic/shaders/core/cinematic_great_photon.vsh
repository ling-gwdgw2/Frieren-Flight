#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 viewPosition;
out float age;
out float energy;
out float span;
out float halfWidth;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9995);
    viewPosition = pos.xyz;
    uv = UV0;
    age = Color.r * 255.0;
    energy = Color.a;
    span = max(Color.g * 512.0, 0.01);
    halfWidth = max(Color.b * 32.0, 0.01);
}
