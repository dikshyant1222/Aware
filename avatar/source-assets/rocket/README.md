# Avatar source
Microsoft Rocketbox Female_Adult_01, MIT licensed.
Upstream: https://github.com/microsoft/Microsoft-Rocketbox/tree/master/Assets/Avatars/Adults/Female_Adult_01
The original FBX is retained here. Deployed model: dist/vendor/rocket/ari-detailed.glb.
Textures were resized to at most 2048 pixels and encoded as WebP quality 92.
Geometry was grouped by material, finger tip markers added, and only hand-region
triangles subdivided using skin-aware Loop refinement (dist/hand-surface.js).
The runtime uses preprocessed GLB, not FBX or a runtime subdivision step.
