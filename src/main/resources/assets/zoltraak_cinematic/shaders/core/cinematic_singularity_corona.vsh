#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

// Zoltraak Cinematic - 100% Clean Room Coronal Singularity Vertex Shader
// Projects demonic coronal accretion halo quad into camera space

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vertexColor;
out float age;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9995);
    uv = UV0;
    vertexColor = Color;
    age = Color.r * 255.0;
}
