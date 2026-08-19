#version 410 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec2 textureCoord;
layout (location = 2) in vec3 normal;
layout (location = 3) in mat4 instanceTransformationMatrix;
layout (location = 7) in vec4 instanceAmbient;
layout (location = 8) in vec4 instanceDiffuse;
layout (location = 9) in vec4 instanceSpecular;
layout (location = 10) in float instanceShininess;

out vec2 fragTextureCoord;
out vec3 fragNormal;
out vec3 fragPos;
out vec3 camDirection;
out float vDepthVisibility;
out vec4 vInstanceAmbient;
out vec4 vInstanceDiffuse;
out vec4 vInstanceSpecular;
out float vInstanceShininess;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 transformationMatrix;
uniform float depthVisibility;
uniform int useInstanceMaterial;

void main() {
	mat4 modelMatrix = useInstanceMaterial == 1 ? instanceTransformationMatrix : transformationMatrix;
	gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
	
	fragPos = vec3(modelMatrix * vec4(position, 1.0));
	vec3 camPosition = vec3(viewMatrix * modelMatrix * vec4(0, 0, 0, 1.0));
	camDirection = normalize(camPosition);
	fragNormal = normalize(mat3(modelMatrix) * normal);
	
	vDepthVisibility = 1 - max(0, (-camPosition * depthVisibility).z);
	
	fragTextureCoord = textureCoord;
	vInstanceAmbient = instanceAmbient;
	vInstanceDiffuse = instanceDiffuse;
	vInstanceSpecular = instanceSpecular;
	vInstanceShininess = instanceShininess;
}
