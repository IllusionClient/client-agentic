#version 120
attribute vec2 corner;
attribute vec4 rect;
attribute vec4 color;
varying vec2 local;
varying vec2 size;
varying vec4 tint;

void main() {
	size = rect.zw - rect.xy;
	local = corner * size;
	tint = color;
	gl_Position = gl_ModelViewProjectionMatrix * vec4(rect.xy + local, 0.0, 1.0);
}
