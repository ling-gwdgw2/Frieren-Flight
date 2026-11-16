#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec3 vEyeNormal;
out vec3 vViewDir;
out vec4 vColor;

void main() {
    vec4 eyePos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePos;
    gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9995);

    vUv = UV0;
    vEyeNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    vViewDir = -eyePos.xyz;
    vColor = Color;
}

