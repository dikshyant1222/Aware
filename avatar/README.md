# Signa — latest project with thumb fix

Includes the restored avatar appearance, 14 common signs, fingerspelling,
hand close-up, hand-only video export, and the latest thumb placement correction.

## Run locally
Extract the ZIP, open a terminal in signa-studio, and run:
    python3 -m http.server 8000 --directory dist

On Windows, use python instead of python3 if needed.
Open http://localhost:8000 in your browser.
No npm install, build step, API key, or backend is required.
Use an HTTP server rather than opening index.html directly.

## Contents
dist/: editable application source, runtime dependencies, avatar and textures.
source-assets/: original avatar assets.
Third-party licenses are included in dist/vendor.
The existing hosting configuration belongs to the original Sites project.
For other static hosting, publish dist/.

## Limitations
Experimental animation, not validated ASL translation.
English word order is preserved; unsupported words use fingerspelling.
Video export captures the right hand only; keep the tab visible during export.
Git history and local caches are excluded.
