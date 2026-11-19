#version 150
// Copyright (c) 2026 Aling7788565. All Rights Reserved.

in vec3 Position;
in vec2 UV0;

out vec2 vUv;

void main() {
    vUv = UV0;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
