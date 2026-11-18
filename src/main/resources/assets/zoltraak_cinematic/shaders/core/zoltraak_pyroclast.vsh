#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vColor;
out vec3 vLocalPos;
out vec3 vViewPos;
out vec3 vCamToFragWorld;

void main() {
    vec4 eyePos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePos;

    if (Color.b > 0.5) {
        // Keep back-face volume raymarch in front of terrain behind the camera when inside blast sphere
        gl_Position.z = -0.96 * gl_Position.w;
    } else {
        gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9995);
    }

    vUv = UV0;
    vColor = Color;
    vLocalPos = Normal;
    vViewPos = eyePos.xyz;
    vCamToFragWorld = eyePos.xyz * mat3(ModelViewMat);
}
