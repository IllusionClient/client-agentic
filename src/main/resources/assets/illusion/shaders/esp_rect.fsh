#version 120
varying vec2 local;
varying vec2 size;
varying vec4 tint;

void main() {
	vec2 edge = min(local, size - local);
	float inset = min(edge.x, edge.y);
	if (inset >= 3.0) discard;

	float outline = 150.0 / 255.0;
	if (inset < 1.0 || inset >= 2.0) {
		gl_FragColor = vec4(0.0, 0.0, 0.0, outline);
	} else {
		float alpha = tint.a + outline * (1.0 - tint.a);
		gl_FragColor = vec4(tint.rgb * tint.a / alpha, alpha);
	}
}
