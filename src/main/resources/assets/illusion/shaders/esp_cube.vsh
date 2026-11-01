#version 120
attribute vec3 corner;
attribute vec3 boxMin;
attribute vec3 boxMax;
attribute vec4 color;
varying vec4 tint;

void main() {
	tint = color;
	gl_Position = gl_ModelViewProjectionMatrix * vec4(mix(boxMin, boxMax, corner), 1.0);
}
